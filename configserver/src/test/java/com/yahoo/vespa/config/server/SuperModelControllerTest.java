// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.cloud.config.LbServicesConfig.Tenants.Applications;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.config.provision.Zone;
import com.yahoo.jrt.Request;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequestV3;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.server.model.SuperModelConfigProvider;
import com.yahoo.vespa.config.server.rpc.UncompressedConfigResponseFactory;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.9
 */
public class SuperModelControllerTest {

    private SuperModelController handler;

    @Before
    public void setupHandler() throws IOException, SAXException {
        Map<TenantName, Map<ApplicationId, ApplicationInfo>> models = new LinkedHashMap<>();
        models.put(TenantName.from("a"), new LinkedHashMap<>());
        File testApp = new File("src/test/resources/deploy/app");
        ApplicationId app = ApplicationId.from(TenantName.from("a"),
                                               ApplicationName.from("foo"), InstanceName.defaultName());
        models.get(app.tenant()).put(app, new ApplicationInfo(app, 4l, new VespaModel(FilesApplicationPackage.fromFile(testApp))));
        SuperModel superModel = new SuperModel(models);
        handler = new SuperModelController(new SuperModelConfigProvider(superModel, Zone.defaultZone()), new TestConfigDefinitionRepo(), 2, new UncompressedConfigResponseFactory());
    }
    
    @Test
    public void test_lb_config_simple() {
        LbServicesConfig.Builder lb = new LbServicesConfig.Builder();
        handler.getSuperModel().getConfig(lb);
        LbServicesConfig lbc = new LbServicesConfig(lb);
        assertThat(lbc.tenants().size(), is(1));
        assertThat(lbc.tenants("a").applications().size(), is(1));
        Applications app = lbc.tenants("a").applications("foo:prod:default:default");
        assertTrue(app.hosts().size() > 0);
    }


    @Test(expected = UnknownConfigDefinitionException.class)
    public void test_unknown_config_definition() {
        String md5 = "asdfasf";
        Request request = JRTClientConfigRequestV3.createWithParams(new ConfigKey<>("foo", "id", "bar", md5, null), DefContent.fromList(Collections.emptyList()),
                                                                    "fromHost", md5, 1, 1, Trace.createDummy(), CompressionType.UNCOMPRESSED,
                                                                    Optional.empty())
                                                  .getRequest();
        JRTServerConfigRequestV3 v3Request = JRTServerConfigRequestV3.createFromRequest(request);
        handler.resolveConfig(v3Request);
    }

    @Test
    public void test_lb_config_multiple_apps() throws IOException, SAXException {
        Map<TenantName, Map<ApplicationId, ApplicationInfo>> models = new LinkedHashMap<>();
        models.put(TenantName.from("t1"), new LinkedHashMap<>());
        models.put(TenantName.from("t2"), new LinkedHashMap<>());
        File testApp1 = new File("src/test/resources/deploy/app");
        File testApp2 = new File("src/test/resources/deploy/advancedapp");
        File testApp3 = new File("src/test/resources/deploy/advancedapp");
        // TODO must fix equals, hashCode on Tenant
        Version vespaVersion = Version.fromIntValues(1, 2, 3);
        models.get(TenantName.from("t1")).put(applicationId("mysimpleapp"),
                new ApplicationInfo(applicationId("mysimpleapp"), 4l, new VespaModel(FilesApplicationPackage.fromFile(testApp1))));
        models.get(TenantName.from("t1")).put(applicationId("myadvancedapp"),
                new ApplicationInfo(applicationId("myadvancedapp"), 4l, new VespaModel(FilesApplicationPackage.fromFile(testApp2))));
        models.get(TenantName.from("t2")).put(applicationId("minetooadvancedapp"),
                new ApplicationInfo(applicationId("minetooadvancedapp"), 4l, new VespaModel(FilesApplicationPackage.fromFile(testApp3))));
        SuperModel superModel = new SuperModel(models);
        SuperModelController han = new SuperModelController(new SuperModelConfigProvider(superModel, Zone.defaultZone()), new TestConfigDefinitionRepo(), 2, new UncompressedConfigResponseFactory());
        LbServicesConfig.Builder lb = new LbServicesConfig.Builder();
        han.getSuperModel().getConfig(lb);
        LbServicesConfig lbc = new LbServicesConfig(lb);
        assertThat(lbc.tenants().size(), is(2));
        assertThat(lbc.tenants("t1").applications().size(), is(2));
        assertThat(lbc.tenants("t2").applications().size(), is(1));
        assertThat(lbc.tenants("t2").applications("minetooadvancedapp:prod:default:default").hosts().size(), is(1));
        assertQrServer(lbc.tenants("t2").applications("minetooadvancedapp:prod:default:default"));
    }

    private ApplicationId applicationId(String applicationName) {
        return ApplicationId.from(TenantName.defaultName(),
                                  ApplicationName.from(applicationName), InstanceName.defaultName());
    }

    private void assertQrServer(Applications app) {
        String host = app.hosts().keySet().iterator().next();
        Applications.Hosts hosts = app.hosts(host);
        assertThat(hosts.hostname(), is(host));
        for (Map.Entry<String, Applications.Hosts.Services> e : app.hosts(host).services().entrySet()) {
            System.out.println(e);
            if ("qrserver".equals(e.getKey())) {
                Applications.Hosts.Services s = e.getValue();
                assertThat(s.type(), is("qrserver"));
                assertThat(s.ports().size(), is(4));
                assertThat(s.index(), is(0));
                return;
            }
        }
        org.junit.Assert.fail("No qrserver service in config");
    }

}




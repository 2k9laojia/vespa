package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author mpolden
 */
public class UpgraderResponse extends HttpResponse {

    private final Upgrader upgrader;

    public UpgraderResponse(Upgrader upgrader) {
        super(200);
        this.upgrader = upgrader;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setDouble("upgradesPerMinute", upgrader.upgradesPerMinute());
        root.setLong("applicationsGivingMinConfidence", upgrader.applicationsGivingMinConfidence());
        root.setLong("applicationsGivingMaxConfidence", upgrader.applicationsGivingMaxConfidence());
        root.setDouble("failureRatioAtMaxConfidence", upgrader.failureRatioAtMaxConfidence());
        new JsonFormat(true).encode(outputStream, slime);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }
}

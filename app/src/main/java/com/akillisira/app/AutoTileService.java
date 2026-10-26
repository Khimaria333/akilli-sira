package com.akillisira.app;

import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Bildirim panelindeki hızlı ayarlar kutucuğu: otomatik geçişi aç/kapat. */
public class AutoTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override
    public void onClick() {
        super.onClick();
        Engine e = Engine.get(this);
        e.setAuto(!e.isAuto());
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        Engine e = Engine.get(this);
        boolean on = e.isAuto();
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Akıllı Sıra");
        if (Build.VERSION.SDK_INT >= 29) {
            String sub;
            if (!on) sub = "Kapalı";
            else if (e.pick != null) sub = "Sıradaki: " + e.pick.title;
            else sub = "Otomatik açık";
            t.setSubtitle(sub);
        }
        t.updateTile();
    }
}

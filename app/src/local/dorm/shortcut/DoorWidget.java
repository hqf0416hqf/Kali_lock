package local.dorm.shortcut;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public final class DoorWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.door_widget);
            views.setTextViewText(R.id.widget_label, Config.live(c) ? "一次开锁" : "开门演练");
            Intent intent = new Intent(c, RunActivity.class);
            PendingIntent pending = PendingIntent.getActivity(c, 7, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_button, pending);
            manager.updateAppWidget(id, views);
        }
    }
    static void refresh(Context c) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        new DoorWidget().onUpdate(c, manager, manager.getAppWidgetIds(new ComponentName(c, DoorWidget.class)));
    }
}

package com.testing.esp32_ia.remote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.testing.esp32_ia.R;
import com.testing.esp32_ia.utils.AfterNotification;


public class Notification {

    Context context;
    Activity activity;
    public Notification(Context context) {
        this.context = context;
    }

    private final String channelId = "i.apps.notifications";
    private final String description = "Text de notificacion";
    private final int notificationId = 1234;
    public void createNotificationChannel(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){ //Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            NotificationChannel notificationChannel = new NotificationChannel(
                    channelId,
                    description,
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationChannel.enableLights(true);//enciende la luz led al recibir notificacion
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.enableVibration(true);

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if(notificationManager != null){
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void sendNotification() {
        // Intent that triggers when the notification is tapped
        Intent intent = new Intent(context, AfterNotification.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Custom layout for the notification content
        RemoteViews contentView = new RemoteViews(context.getPackageName(), R.layout.activity_after_notification);

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Notification icon
                .setContent(contentView) // Custom notification content
                .setContentTitle("Pregunta 1") // Title displayed in the notification
                .setContentText("Respuesta: a") // Text displayed in the notification
                .setContentIntent(pendingIntent) // Pending intent triggered when tapped
                .setAutoCancel(true) // Dismiss notification when tapped
                .setPriority(NotificationCompat.PRIORITY_HIGH); // Notification priority for better visibility

        // Display the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(notificationId, builder.build());
    }
}

package com.testing.esp32_ia.remote;
import android.content.Context;
import android.net.*;

public class RED {

    public void forceCellularNetwork(Context context) {

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build();

        cm.requestNetwork(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                ConnectivityManager.setProcessDefaultNetwork(network);
            }
        });
    }
}

package btools.routingapp; // Questa riga è FONDAMENTALE

import android.os.Bundle;

interface IBRouterService {
    String getTrackFromParams(in Bundle params);
}
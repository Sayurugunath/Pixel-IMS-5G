package dev.bluehouse.enablevolte;

import android.os.IBinder;

interface IPrivilegedService {
    IBinder getSystemService(String name);
    int getServiceUid();
    String getAllowedSystemProperty(String name);
    boolean setAllowedSystemProperty(String name, String value);
    String getTelephonyDiagnosticSnapshot(String kind);
    String getRegionalModemPatchStatus();
    String installRegionalModemPatch();
    String scheduleRegionalModemPatchRemoval();
}

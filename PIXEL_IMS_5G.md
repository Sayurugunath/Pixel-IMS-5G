# Pixel IMS 5G

Pixel IMS 5G is an experimental, root- or Shizuku-powered Android app for Google Tensor Pixels. This build is tested on a rooted Pixel 7 Pro running Android 17.

Version 0.12.10F restores the complete per-SIM control entry points while keeping Monitoring fully separate. Version 0.12.6 introduced the corrected Android application ID `com.nirmala.pixel5gims`; only that migration requires users of `com.fibc.pixel5gims` to uninstall the old application, install 0.12.6, and approve Root or Shizuku again. Existing 0.12.6 installations update normally. Version 0.12.5 added a two-minute, 24-sample field test with NRARFCN/frequency, SS/CSI measurements, registration and reject state, callback events, per-sample PCC/SCC and TelephonyRegistry evidence, CarrierConfig gates, and start/end root radio evidence.

## Features

- Select NSA only, SA only, NSA + SA, or disabled carrier configuration per SIM.
- Choose NSA/5G preferred while retaining LTE fallback.
- Choose experimental SA/NR-only mode.
- Restore the user radio mask that was active before the app changed it.
- Enable VoLTE and VoNR and restart IMS registration.
- Retain the upstream Pixel IMS carrier-config editor and diagnostics.
- Apply per-SIM LTE and NR band restrictions from a dedicated Bands tab.
- Read the Tensor modem's OEM LTE carrier-aggregation enablement status.
- Verify band restrictions after applying them and report when the modem rejects them.
- Show serving and nearby-cell LTE/NR bands reported by the modem.
- Distinguish NR advertisement, EN-DC/NSA eligibility, and active SA registration.
- Apply one-tap NSA-only/LTE+NR preference or experimental SA/NR-only mode on Android 17.
- Contact the developer and report feedback from the in-app About screen.
- Check GitHub Releases and download signed APK updates from inside the app.
- Reset band selection to automatic at any time.
- Use round Auto, Force NSA, and Force SA choices per SIM.
- Highlight every modem-reported band in green, selected or not and including Automatic mode.
- Apply VoLTE and LTE CA through Easy Mode and grey out advanced controls while it is active.
- Diagnose common IMS failures and restore Google/carrier defaults from a FIX action.
- Undo the last radio or band change when it removes cellular service.
- Restore all active SIMs, clear app recovery data, and reboot from the top recovery action.
- Show serving and neighbor cell identity, channel, band, PCI, TAC, RSRP, RSRQ, SINR, and a live signal history.
- Keep IWLAN (IMS over Wi-Fi), Wi-Fi frequency, and the cellular anchor separate so an “IWLAN 1800” label is not mistaken for a Wi-Fi band.
- Recognize Sri Lankan MCC-MNC profiles for Dialog (41302), SLT-MOBITEL (41301), Airtel Lanka (41305), and Hutch (41308).
- Audit the User, Power, Carrier, 2G-control, and Test network-type gates in Root mode.
- Force LTE + NR through every gate Android 17 exposes, set the approved IMS debug properties, and distinguish a local policy block from missing EN-DC/network acceptance.
- Restore the pre-force network masks, band selection, carrier NR modes, and system properties.
- Save a field-test report in Downloads with visible tower IDs, bands/channels, signal metrics, CA, NR state, EN-DC, IMS transport, relevant CarrierConfig values, network masks, device build, and baseband.
- Exclude phone number, IMSI, and ICCID from field-test reports and warn that tower IDs can reveal approximate location.
- Show LTE+ only from confirmed carrier aggregation, 5G NSA/SA only from connected NR state, and VoWiFi only from confirmed IMS-over-IWLAN registration.

## Install and use

1. Prepare either a compatible root manager or Shizuku using Wireless debugging/ADB.
2. Install the APK and open **Pixel IMS 5G**.
3. Choose Root or Shizuku and approve access.
4. Open the SIM tab.
5. Set **5G NR architecture** to **NSA + SA** and enable **VoLTE**.
6. Set **Preferred radio mode** to **NSA/5G preferred (LTE + NR)**.
7. Restart IMS registration or reboot the phone if IMS does not register immediately.

The **Bands** tab provides selectable LTE and NR chips. Selecting several LTE bands requests eligible carrier-aggregation candidates, but cannot force a specific CA combination; that decision remains with the modem and network. Pixel firmware may accept the standard Android request and then discard it. The app verifies the retained restriction and reports rejection. Always use **Automatic** before travelling or when service disappears.

The detected-band list contains only cells the modem currently reports; it is not a complete spectrum scan. **Force NSA preference** enables the NSA carrier profile and allows LTE+NR. **Force SA-only** permits only NR and is deliberately disruptive. Neither mode can make a network accept registration or supply EN-DC on a cell where the carrier has disabled it.

### Root Force Lab

Root Force is the strongest model-independent approach the app can safely apply across Tensor generations. It forces every Android-side LTE/NR mask that the current OS exposes, sets CarrierConfig to NSA + SA, enables fixed allow-listed IMS properties, restores automatic band scanning, and restarts IMS. Android 17 CarrierConfig changes are applied through the instrumentation permission broker because UID 0 has no Android package identity for the platform's package/feature check.

This does not write Shannon NV/EFS. Shannon NV item names and meanings vary by Pixel model and modem build; using a script made for another device can crash or disable the modem. A future NV layer must use a signed profile matching the exact device, baseband, and firmware, take a backup first, and provide a tested recovery path. Root cannot create an RF signal, make an LTE cell advertise EN-DC, add unsupported hardware bands, or make a carrier authenticate a SIM/IMS account.

Band diagnostics request a fresh modem measurement. If Android omits a band number but exposes the LTE EARFCN or NR-ARFCN, the app derives the operating band using the platform frequency mapping.

NR-only mode can leave the phone without calls, SMS, or data when standalone 5G is unavailable. It does not create coverage, bypass a carrier IMEI allowlist, or guarantee IMS registration. Carrier and network support are still required.

## Build

The project compiles with Android SDK 36 and JDK 17 or newer. It needs a full/patched Android 36 `android.jar` because it calls hidden telephony binder interfaces. Place that jar at `platforms/android-36/android.jar`, set `sdk.dir` in `local.properties`, and run:

```powershell
.\gradlew.bat assembleDebug
```

## Origin and license

Developed by **Nadeeja Nirmala** — [GitHub](https://github.com/barrylk) · [Facebook](https://www.facebook.com/nirmalafromslk/) · [Issues and feedback](https://github.com/barrylk/Pixel-IMS-5G/issues).

This project is a modified version of [kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch), based on commit `0b4b5fef31e4e4904eece60bdb360ea3111ac3aa`. Modifications add radio-mode control, 5G carrier configuration, a unique application ID, and offline version display.

The complete project remains licensed under GNU GPL v3. See `LICENSE`. Pixel is a trademark of Google LLC; this project is unofficial and is not affiliated with Google or any carrier.

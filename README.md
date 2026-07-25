# Pixel IMS 5G

An experimental root- or Shizuku-powered IMS and radio configuration app for Google Tensor Pixel phones. It uses model-independent Android telephony interfaces for Pixel 6 through Pixel 10 and is developed and tested on a rooted Pixel 7 Pro running Android 17.

Android application ID: `com.nirmala.pixel5gims`.

## Features

- Enable VoLTE, VoNR, VoWiFi, NSA and SA carrier configuration.
- Apply NSA/LTE+NR preference or experimental SA/NR-only mode.
- Show live serving radio, LTE/NR bands, NR advertisement and EN-DC eligibility.
- Actively refresh cell measurements and infer omitted bands from EARFCN/NR-ARFCN.
- Request LTE/NR band restrictions and detect when Pixel firmware rejects them.
- Select LTE and NR bands using chips; every currently reported band stays green, including in Automatic mode.
- Enable per-SIM Easy Mode to apply VoLTE, enhanced LTE/LTE+, automatic bands, LTE+NR allowance, and verified Tensor CA enablement together.
- Lock advanced radio and band controls while Easy Mode is active, then unlock them without disabling calling settings.
- Preserve the original Tensor CA state so Undo and Restore All can return the modem to its previous value.
- Configure and recover each active SIM independently.
- Explain common IMS registration failures and restore Google/carrier defaults with one tap.
- Detect loss of service after an app change and undo the exact previous radio state.
- Restore every active SIM, clear the app's recovery state, and reboot from a guarded recovery action.
- Read the Tensor modem's LTE carrier-aggregation enablement status.
- Material 3 Expressive interface with dynamic Pixel colors and glass surfaces.
- Check GitHub Releases and download signed in-app updates.
- Choose Root or Shizuku at first launch and change the backend later from About.
- Monitor both SIMs live: IMS registration transport, VoWiFi/IWLAN state, Wi-Fi frequency, LTE/NR cells, signal metrics and history, and NSA/EN-DC advertisement.
- Detect Dialog, SLT-MOBITEL, Airtel Lanka, and Hutch SIM identities and show conservative Sri Lankan band and activation guidance.
- Apply a safe Sri Lankan/global compatibility profile without locking bands or claiming to bypass carrier provisioning.
- Use a per-SIM Root Force Lab to snapshot, force, audit, verify, and restore every Android-side LTE/NR gate exposed on Android 17.
- Run a two-minute, 24-sample 5G field test and export a privacy-labelled report to `Downloads/Pixel IMS 5G` with NRARFCN/frequency, SS/CSI measurements, registration/reject state, callback events, PCC/SCC history, CarrierConfig gates, and sanitized root evidence.
- Use the separate Monitoring section for TelephonyRegistry events, evidence-based 5G attach tracing, effective-vs-Android-default CarrierConfig differences, NR capability decoding, and root-only PCC/SCC physical channels.
- In Root mode, capture sanitized TelephonyRegistry, phone-service, and radio-log evidence. Shizuku mode clearly marks phone-process and `READ_PRECISE_PHONE_STATE` internals as unavailable.
- Display LTE+ only when Android confirms carrier aggregation, 5G NSA/SA only from connected NR state, and VoWiFi only from active IMS-over-IWLAN registration.

## Install

1. Have either a compatible root manager (Magisk, KernelSU, or APatch) or install and start [Shizuku](https://shizuku.rikka.app/).
2. Download the latest APK from [Releases](https://github.com/barrylk/Pixel-IMS-5G/releases/latest).
3. Install Pixel IMS 5G, choose Root or Shizuku, and approve that backend.

Changing radio modes can remove calls, SMS, or data. The app can change modem preferences and carrier configuration, but cannot create network coverage, EN-DC support, carrier authorization, or SA registration.

Root Force deliberately does not write modem NV/EFS. Those values are device- and baseband-specific; a mismatched Shannon NV profile can crash or disable the modem. The safe extension path is an exact-model, exact-baseband signed profile with backup and recovery—not a universal NV script.

## Developer and support

Developed by **Nadeeja Nirmala**.

- [GitHub](https://github.com/barrylk)
- [Facebook](https://www.facebook.com/nirmalafromslk/)
- [Bug reports and feedback](https://github.com/barrylk/Pixel-IMS-5G/issues)

## Origin and license

Pixel IMS 5G is based on the original GPL project [kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch) and the work of its community contributors. This modified project remains licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).

This is an unofficial community project and is not affiliated with Google or any mobile carrier.

## Band scan limitations

The app requests a fresh cell-information measurement and lists every LTE/NR band the Android radio layer returns. When the radio omits a band but exposes an EARFCN or NR-ARFCN, the app derives the band using Android's radio-frequency mapping. No Android app can display a transmitter the modem does not measure or deliberately withholds from the framework.

`IWLAN` means the IMS service is registered through Wi-Fi; it is not an LTE/NR band. The monitor deliberately shows IMS transport, Wi-Fi frequency, and any cellular anchor as separate values.

# Correzione Altitudine GPS (SLM vs Elissoide)

L'obiettivo è garantire che l'altitudine mostrata nel cruscotto sia riferita al livello del mare (MSL) e non all'ellissoide WGS84.

## Proposed Changes

### [Component] Database / Repository

#### [MODIFY] [LocationRepository.kt](file:///C:/AndroidStudioProjects/Sentieri/app/src/main/java/com/apstudio/sentieri/db/LocationRepository.kt)
- Aggiunta variabile `geoidSeparation` per memorizzare la differenza tra ellissoide e geoide.
- Modifica di `processNewLocation` per:
    1. Usare `location.mslAltitudeMeters` se disponibile (Android 14+).
    2. Usare `geoidSeparation` per correggere l'altitudine ellissoidale.
    3. Evitare il fallback automatico all'altitudine ellissoidale grezza se un valore MSL è già noto.

### [Component] Background Services

#### [MODIFY] [LocationService.kt](file:///C:/AndroidStudioProjects/Sentieri/app/src/main/java/com/apstudio/sentieri/LocationService.kt)
- Aggiornamento `initializeLocationListener` per estrarre `mslAltitudeMeters` su API 34+.
- Aggiornamento `parseGPGGA` per estrarre e trasmettere il `geoidSeparation`.

## Verification Plan

### Manual Verification
- Test su dispositivo con Android 14+: verificare che l'altitudine coincida con quella attesa s.l.m.
- Test su versioni precedenti: verificare tramite log che il messaggio NMEA GGA aggiorni correttamente la quota e che questa non venga sovrascritta dal fix GPS successivo.
- Verificare che il cruscotto mostri valori coerenti (es. non ci siano salti di 40-50m all'arrivo del primo messaggio NMEA).

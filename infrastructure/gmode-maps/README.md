# GMODE local map service

VM 106 hosts both local services:

- OSRM routing: `http://OSRM_VM_IP:5000`
- Ontario PMTiles: `http://OSRM_VM_IP:8080/maps/ontario.pmtiles`
- Map-service health: `http://OSRM_VM_IP:8080/health.json`

The map service is intentionally lightweight: Nginx serves one bounded PMTiles archive with HTTP Range and CORS support. Home Assistant's Leaflet pages render that vector archive through the vendored Protomaps Leaflet adapter. No CARTO or public OpenStreetMap tile endpoint is used at runtime.

The archive is extracted from the dated Protomaps daily build with:

```bash
pmtiles extract \
  https://build.protomaps.com/20260829.pmtiles \
  /opt/gmode-maps/maps/ontario.pmtiles.next \
  --bbox=-95.16,41.68,-74.34,56.86 \
  --maxzoom=15
```

After `pmtiles verify` succeeds, atomically rename the `.next` file to `ontario.pmtiles`, update `health.json`, and reload Nginx. Keep the previous archive until the new one is verified.

## Validation

```powershell
Invoke-RestMethod http://OSRM_VM_IP:8080/health.json
curl.exe -I -H "Range: bytes=0-126" http://OSRM_VM_IP:8080/maps/ontario.pmtiles
Invoke-RestMethod 'http://OSRM_VM_IP:5000/route/v1/driving/START_LON,START_LAT;END_LON,END_LAT?overview=false'
```

The map and routing addresses are LAN-only. A phone away from home needs a VPN or an app-downloaded offline PMTiles copy. Do not expose either service directly to the public internet.

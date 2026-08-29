(function (global) {
  "use strict";

  const DEFAULT_URL = "http://OSRM_VM_IP:8080/maps/ontario.pmtiles";

  // Leaflet's CSS zoom transition can race PMTiles canvas removal when several
  // maps initialize together. Disabling the animation keeps zooming immediate
  // and prevents stale transition callbacks from touching a removed map pane.
  if (global.L && global.L.Map && typeof global.L.Map.mergeOptions === "function") {
    global.L.Map.mergeOptions({
      zoomAnimation: false,
      fadeAnimation: false,
      markerZoomAnimation: false,
    });
  }

  function addLocalBasemap(map, options) {
    if (!global.protomapsL || typeof global.protomapsL.leafletLayer !== "function") {
      throw new Error("Local map adapter failed to load");
    }

    const settings = Object.assign(
      {
        url: DEFAULT_URL,
        flavor: "dark",
        lang: "en",
        attribution:
          '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, local Protomaps archive',
      },
      options || {}
    );

    return global.protomapsL.leafletLayer(settings).addTo(map);
  }

  global.GMODE_MAPS = Object.freeze({
    pmtilesUrl: DEFAULT_URL,
    addLocalBasemap,
  });
})(window);

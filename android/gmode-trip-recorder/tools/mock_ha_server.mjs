import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { createServer } from "node:http";

const port = 18123;
const token = "gmode-test-token";
const reportPath = resolve(process.argv[2] ?? "app/build/reports/device-test/mock-upload.json");
const uploads = [];

const server = createServer((request, response) => {
  if (request.method !== "POST" || request.url !== "/api/gmode_trip_recorder/mobile/upload") {
    response.writeHead(404, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ error: "Not found" }));
    return;
  }
  if (request.headers.authorization !== `Bearer ${token}`) {
    response.writeHead(401, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ error: "Unauthorized" }));
    return;
  }

  const chunks = [];
  request.on("data", (chunk) => chunks.push(chunk));
  request.on("end", () => {
    try {
      const payload = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      if (payload.protocolVersion !== 1 || !payload.trip?.id || !Array.isArray(payload.points)) {
        throw new Error("Invalid mobile upload payload");
      }
      const pointIds = payload.points.map((point) => point.pointId).filter(Boolean);
      const upload = {
        receivedAt: new Date().toISOString(),
        protocolVersion: payload.protocolVersion,
        appVersion: payload.appVersion,
        deviceIdPresent: Boolean(payload.deviceId),
        tripId: payload.trip.id,
        tripStatus: payload.trip.status,
        pointCount: payload.points.length,
        pointIds,
        telemetry: payload.points.map((point) => ({
          accuracyMeters: point.accuracyMeters,
          altitudeMeters: point.altitudeMeters,
          pressureHpa: point.pressureHpa,
          accelerationRmsMs2: point.accelerationRmsMs2,
          gyroscopePeakRadS: point.gyroscopePeakRadS,
          batteryPercent: point.batteryPercent,
          networkType: point.networkType,
        })),
      };
      uploads.push(upload);
      const report = {
        requestCount: uploads.length,
        totalPointCount: uploads.reduce((total, item) => total + item.pointCount, 0),
        uploads,
      };
      mkdirSync(dirname(reportPath), { recursive: true });
      writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
      response.writeHead(200, { "Content-Type": "application/json" });
      response.end(JSON.stringify({ acknowledgedPointIds: pointIds }));
    } catch (error) {
      response.writeHead(400, { "Content-Type": "application/json" });
      response.end(JSON.stringify({ error: error.message }));
    }
  });
});

server.listen(port, "0.0.0.0", () => {
  console.log(`GMODE mock Home Assistant ready on port ${port}`);
});

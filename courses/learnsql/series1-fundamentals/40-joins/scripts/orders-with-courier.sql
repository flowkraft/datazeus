SELECT o."OrderID", s."CompanyName", o."Freight"
FROM "Orders" o
JOIN "Shippers" s
  ON s."ShipperID" = o."ShipVia"
ORDER BY o."Freight" DESC
LIMIT 5;

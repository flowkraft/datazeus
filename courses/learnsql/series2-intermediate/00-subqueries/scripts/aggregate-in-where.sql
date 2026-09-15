SELECT "ProductName", "UnitPrice"
FROM "Products"
WHERE "UnitPrice" > AVG("UnitPrice");

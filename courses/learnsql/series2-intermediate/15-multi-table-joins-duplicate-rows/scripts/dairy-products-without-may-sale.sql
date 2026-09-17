SELECT count(*) AS "No May sale"
FROM "Products" p
LEFT JOIN (SELECT d."ProductID",
                  sum(d."Quantity")
                    AS "May units"
           FROM "Order Details" d
           JOIN "Orders" o
             ON o."OrderID" = d."OrderID"
           WHERE o."OrderDate" >= DATE '2024-05-01'
             AND o."OrderDate" <  DATE '2024-06-01'
           GROUP BY d."ProductID") AS m
  ON m."ProductID" = p."ProductID"
WHERE p."CategoryID" = 4
  AND m."May units" IS NULL;

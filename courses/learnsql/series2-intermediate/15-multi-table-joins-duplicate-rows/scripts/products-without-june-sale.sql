SELECT count(*) AS "No June sale"
FROM "Products" p
LEFT JOIN (SELECT d."ProductID",
                  sum(d."Quantity")
                    AS "June units"
           FROM "Order Details" d
           JOIN "Orders" o
             ON o."OrderID" = d."OrderID"
           WHERE o."OrderDate" >= DATE '2024-06-01'
             AND o."OrderDate" <  DATE '2024-07-01'
           GROUP BY d."ProductID") AS j
  ON j."ProductID" = p."ProductID"
WHERE j."June units" IS NULL;

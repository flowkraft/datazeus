WITH customer_totals AS (
  SELECT o."CustomerID",
         SUM(d."UnitPrice" * d."Quantity"
           * (1 - d."Discount")) AS "Total"
  FROM "Orders" o
  JOIN "Order Details" d
    ON d."OrderID" = o."OrderID"
  GROUP BY o."CustomerID"
)
SELECT c."CompanyName",
       ROUND(t."Total", 2) AS "Total sales"
FROM customer_totals t
JOIN "Customers" c
  ON c."CustomerID" = t."CustomerID"
WHERE t."Total" > (SELECT AVG("Total")
                   FROM customer_totals)
ORDER BY "Total sales" DESC;

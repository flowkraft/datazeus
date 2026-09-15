SELECT count(*) AS "No ship date",
       SUM(CASE WHEN "RequiredDate" <
                  (SELECT max("OrderDate")
                   FROM "Orders")
           THEN 1 ELSE 0 END)
         AS "Past required date"
FROM "Orders"
WHERE "ShippedDate" IS NULL;

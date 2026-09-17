SELECT count(*) AS "Orders",
       count(DISTINCT "Freight")
         AS "Freight values"
FROM "Orders";

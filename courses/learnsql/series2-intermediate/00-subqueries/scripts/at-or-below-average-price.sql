SELECT count(*) AS "Products"
FROM "Products"
WHERE "UnitPrice" <= (SELECT AVG("UnitPrice")
                      FROM "Products");

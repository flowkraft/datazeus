SELECT count(*) AS "Not heavy"
FROM (SELECT CASE
               WHEN "Freight" > 50
               THEN 'heavy'
               ELSE 'standard'
             END AS "Label"
      FROM "Orders") AS t
WHERE "Label" <> 'heavy';

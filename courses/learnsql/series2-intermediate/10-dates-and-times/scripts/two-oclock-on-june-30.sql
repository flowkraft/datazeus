SELECT TIMESTAMP '2024-06-30 14:00:00'
         BETWEEN '2024-06-01' AND '2024-06-30'
         AS "BETWEEN",
       TIMESTAMP '2024-06-30 14:00:00'
         >= '2024-06-01'
         AND TIMESTAMP '2024-06-30 14:00:00'
           < '2024-07-01'
         AS "Half-open";

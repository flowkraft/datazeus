SELECT (SELECT SUM("Freight")
        FROM "Orders") AS "Orders alone",
       (SELECT SUM(o."Freight")
        FROM "Orders" o
        JOIN "Order Details" d
          ON d."OrderID" = o."OrderID")
         AS "After the join";

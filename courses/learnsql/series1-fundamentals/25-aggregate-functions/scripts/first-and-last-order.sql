SELECT min("OrderDate") AS "First order",
       max("OrderDate") AS "Latest order"
FROM "Orders";

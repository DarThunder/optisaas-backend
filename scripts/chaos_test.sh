#!/bin/bash

BASE_URL="http://localhost:8080/api"
COOKIE_FILE="cookies_chaos.txt"
USER="admin@opti.com"
PASS="123456"
PIN="1234"
PRODUCT_ID=1
CONCURRENCY=20

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}=== PROTOCOLO DE CAOS: THE STRESS TEST ===${NC}"

echo -n "Autenticando... "
curl -s -c $COOKIE_FILE -X POST "$BASE_URL/auth/login" \
     -H "Content-Type: application/json" -d "{\"email\": \"$USER\", \"password\": \"$PASS\"}" > /dev/null
curl -s -b $COOKIE_FILE -c $COOKIE_FILE -X POST "$BASE_URL/auth/select-branch" \
     -H "Content-Type: application/json" -d "{\"branchId\": 1, \"branchPin\": \"$PIN\"}" > /dev/null
echo -e "${GREEN}OK${NC}"

echo -e "\n${YELLOW}[ESCENARIO A] Disparando $CONCURRENCY ventas simultáneas del mismo producto...${NC}"
echo "El objetivo es ver si el sistema vende más stock del que existe."

JSON_VENTA='{
  "clientId": 1,
  "items": [ { "productId": '$PRODUCT_ID', "quantity": 1 } ],
  "payments": [ { "amount": 150.00, "method": "CASH" } ]
}'

rm -f results.txt
touch results.txt

for i in $(seq 1 $CONCURRENCY); do
   (
     HTTP_CODE=$(curl -s -b $COOKIE_FILE -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/sales" \
       -H "Content-Type: application/json" -d "$JSON_VENTA")
     echo $HTTP_CODE >> results.txt
   ) &
done

wait

SUCCESS_COUNT=$(grep -c "200" results.txt)
FAIL_SERVER=$(grep -c "500" results.txt)
FAIL_CLIENT=$(grep -c "400" results.txt)

echo -e "Resultados Escenario A:"
echo -e "  Ventas Exitosas (200): ${GREEN}$SUCCESS_COUNT${NC} (Se llevó todo el stock)"
echo -e "  Rechazos por Stock (400): ${GREEN}$FAIL_CLIENT${NC} (El sistema protegió el inventario)"
echo -e "  Errores Críticos (500): ${RED}$FAIL_SERVER${NC}"
echo -e "  >> REVISA TU STOCK EN DB. Si tenías menos de $SUCCESS_COUNT unidades, el test falló (sistema vulnerable)."

echo -e "\n${YELLOW}[ESCENARIO B] Intentando pagar la misma venta 5 veces al mismo tiempo...${NC}"

SALE_ID=$(curl -s -b $COOKIE_FILE -X POST "$BASE_URL/sales" \
    -H "Content-Type: application/json" \
    -d '{
      "clientId": 1,
      "items": [ { "productId": '$PRODUCT_ID', "quantity": 1 } ],
      "parkSale": true
    }' | grep -o '"saleId":[0-9]*' | grep -o '[0-9]*')

if [ -z "$SALE_ID" ]; then
    echo -e "${RED}Error: No se pudo crear la venta base para el test.${NC}"
    exit 1
fi

echo "  Venta Base creada ID: $SALE_ID. Intentando pagar $150 (Total Deuda) 5 veces..."

JSON_PAGO='{ "amount": 150.00, "method": "CASH" }'
rm -f payments.txt

for i in {1..5}; do
   (
     HTTP_CODE=$(curl -s -b $COOKIE_FILE -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/sales/$SALE_ID/payments" \
       -H "Content-Type: application/json" -d "$JSON_PAGO")
     echo $HTTP_CODE >> payments.txt
   ) &
done

wait

PAY_OK=$(grep -c "200" payments.txt)
PAY_FAIL=$(grep -c "500" payments.txt)

echo -e "Resultados Escenario B:"
echo -e "  Pagos Aceptados: ${GREEN}$PAY_OK${NC} (Debería ser SOLO 1)"
echo -e "  Pagos Rechazados: ${RED}$PAY_FAIL${NC}"

if [ "$PAY_OK" -gt 1 ]; then
    echo -e "${RED}  !! CRÍTICO: Se aceptó más de un pago completo. La venta tiene saldo negativo.${NC}"
else
    echo -e "${GREEN}  ✔ Sistema Robusto. Solo pasó un pago.${NC}"
fi

echo -e "\n=== FIN DEL CAOS ==="

rm payments.txt result.txt $COOKIE_FILE
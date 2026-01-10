#!/bin/bash

URL="http://localhost:8080/api"
COOKIES="cookies_optom.txt"
EMAIL="admin@opti.com"
PASS="123456"
PIN="1234"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}>>> INICIANDO TEST DE FLUJO OPTOMETRISTA${NC}"
rm -f $COOKIES

echo -e "\n1. Logueando usuario ($EMAIL)..."
curl -s -c $COOKIES -X POST "$URL/auth/login" \
     -H "Content-Type: application/json" \
     -d "{\"email\": \"$EMAIL\", \"password\": \"$PASS\"}" > /dev/null

if grep -q "optisaas-auth-token" $COOKIES; then
    echo -e "${GREEN}✔ Login exitoso (Cookie PRE_AUTH recibida)${NC}"
else
    echo -e "${RED}✘ Falló el login${NC}"
    exit 1
fi

echo -e "\n2. Ingresando a Sucursal Central (PIN: $PIN)..."
RESPONSE=$(curl -s -b $COOKIES -c $COOKIES -X POST "$URL/auth/select-branch" \
     -H "Content-Type: application/json" \
     -d "{\"branchId\": 1, \"branchPin\": \"$PIN\"}")

if echo "$RESPONSE" | grep -q "Login completado"; then
    echo -e "${GREEN}✔ Acceso a sucursal concedido (Cookie FULL recibida)${NC}"
else
    echo -e "${RED}✘ Error al entrar a sucursal: $RESPONSE${NC}"
    exit 1
fi

echo -e "\n3. Creando receta para el Cliente ID 1..."
RECETA_JSON='{
    "clientId": 1,
    "sphereRight": -2.50,
    "sphereLeft": -2.00,
    "cylinderRight": -0.50,
    "cylinderLeft": -0.75,
    "axisRight": 90,
    "axisLeft": 100,
    "addition": 0.0,
    "pupillaryDistance": 62.0,
    "notes": "Paciente reporta visión borrosa lejana."
}'

RESPONSE=$(curl -s -b $COOKIES -X POST "$URL/clinical/records" \
     -H "Content-Type: application/json" \
     -d "$RECETA_JSON")

if echo "$RESPONSE" | grep -q "Receta guardada"; then
    echo -e "${GREEN}✔ Receta creada exitosamente${NC}"
else
    echo -e "${RED}✘ Error al crear receta: $RESPONSE${NC}"
    exit 1
fi

RECETA_ID=1

echo -e "\n4. Calculando precio para Receta #$RECETA_ID (Material: CR39 + BlueBlock)..."

CALCULO_JSON="{
    \"clinicalRecordId\": $RECETA_ID,
    \"material\": \"CR39\",
    \"treatment\": \"BlueBlock\"
}"

PRICE_RESPONSE=$(curl -s -b $COOKIES -X POST "$URL/clinical/calculate-price" \
     -H "Content-Type: application/json" \
     -d "$CALCULO_JSON")

echo -e "Respuesta del Servidor:"
echo $PRICE_RESPONSE

if echo "$PRICE_RESPONSE" | grep -q "calculatedPrice"; then
    echo -e "${GREEN}✔ Cálculo exitoso. ¡El Optometrista ha terminado su trabajo!${NC}"
else
    echo -e "${RED}✘ Error en cálculo (¿Falta PriceMatrix o Producto BlueBlock?)${NC}"
fi

echo -e "\n${GREEN}>>> FIN DEL TEST${NC}"

rm $COOKIES
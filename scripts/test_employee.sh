#!/bin/bash

URL="http://localhost:8080/api"
COOKIE_JAR="cookies_test.txt"
ADMIN_USER="admin@opti.com"
ADMIN_PASS="123456"
ADMIN_PIN="1234"

EMP_ID="10050"
EMP_PASS="1234"
EMP_NAME="Juan Perez"
BRANCH_ID=1

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=================================================${NC}"
echo -e "${BLUE}   OPTISAAS - TEST DE FLUJO EMPLEADO (ID 10050)  ${NC}"
echo -e "${BLUE}=================================================${NC}"

echo -e "\n${BLUE}[SETUP] Logueando como Admin para crear al empleado...${NC}"
rm -f $COOKIE_JAR

curl -s -c $COOKIE_JAR -X POST "$URL/auth/login" \
     -H "Content-Type: application/json" \
     -d "{\"identifier\": \"$ADMIN_USER\", \"password\": \"$ADMIN_PASS\"}" > /dev/null

curl -s -b $COOKIE_JAR -c $COOKIE_JAR -X POST "$URL/auth/select-branch" \
     -H "Content-Type: application/json" \
     -d "{\"branchId\": $BRANCH_ID, \"branchPin\": \"$ADMIN_PIN\"}" > /dev/null

echo -e "${BLUE}[SETUP] Creando empleado ID: $EMP_ID ...${NC}"
CREATE_RES=$(curl -s -b $COOKIE_JAR -X POST "$URL/users/create" \
     -H "Content-Type: application/json" \
     -d "{
        \"fullName\": \"$EMP_NAME\", 
        \"username\": \"$EMP_ID\", 
        \"password\": \"$EMP_PASS\", 
        \"role\": \"SELLER\", 
        \"branchId\": $BRANCH_ID 
     }")

rm -f $COOKIE_JAR
echo -e "${GREEN}>>> Setup finalizado. Iniciando simulación de Frontend...${NC}"

echo -e "\n${BLUE}[PASO 1] Empleado ingresa ID ($EMP_ID) y Password...${NC}"

LOGIN_RES=$(curl -s -c $COOKIE_JAR -w "%{http_code}" -X POST "$URL/auth/login" \
     -H "Content-Type: application/json" \
     -d "{\"identifier\": \"$EMP_ID\", \"password\": \"$EMP_PASS\"}")

HTTP_CODE=${LOGIN_RES: -3}
BODY=${LOGIN_RES:0:${#LOGIN_RES}-3}

if [ "$HTTP_CODE" == "200" ]; then
    echo -e "${GREEN}✔ Credenciales Válidas. Cookie PRE_AUTH recibida.${NC}"
else
    echo -e "${RED}✘ Falló el login (HTTP $HTTP_CODE).${NC}"
    echo "Respuesta: $BODY"
    exit 1
fi

echo -e "\n${BLUE}[PASO 2] Frontend solicita acceso a Branch $BRANCH_ID (SIN ENVIAR PIN)...${NC}"

SELECT_RES=$(curl -s -b $COOKIE_JAR -c $COOKIE_JAR -w "%{http_code}" -X POST "$URL/auth/select-branch" \
     -H "Content-Type: application/json" \
     -d "{
        \"branchId\": $BRANCH_ID,
        \"branchPin\": \"\" 
     }")

HTTP_CODE=${SELECT_RES: -3}
BODY=${SELECT_RES:0:${#SELECT_RES}-3}

if [[ "$BODY" == *"Login completado"* ]] && [[ "$HTTP_CODE" == "200" ]]; then
    echo -e "${GREEN}✔ ¡ÉXITO! Acceso concedido SIN PIN.${NC}"
    echo -e "${GREEN}✔ Token FULL generado. El empleado está en el Dashboard.${NC}"
    
    echo -e "\nDatos retornados por el servidor:"
    echo "$BODY"
else
    echo -e "${RED}✘ Falló el acceso a la sucursal.${NC}"
    echo -e "${RED}Es probable que el Backend aún esté pidiendo el PIN o el usuario no tenga Rol.${NC}"
    echo "Respuesta: $BODY"
    exit 1
fi

echo -e "\n${BLUE}=================================================${NC}"
echo -e "${GREEN}       PRUEBA COMPLETADA SATISFACTORIAMENTE      ${NC}"
echo -e "${BLUE}=================================================${NC}"

rm -f $COOKIE_JAR
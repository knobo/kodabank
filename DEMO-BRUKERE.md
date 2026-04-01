# Demo-brukere KodaBank

## Fjordbanken (`fjordbank`)

| Navn | Personnummer | Keycloak-bruker | Passord |
|------|-------------|-----------------|---------|
| Ola Nordmann | 01019012345 | ola@fjordbank | demo |
| Kari Hansen | 15038945678 | kari@fjordbank | demo |

### Kontoer

**Ola Nordmann:**
| Konto | IBAN | Type | Saldo |
|-------|------|------|-------|
| Brukskonto | NO7786010000019 | Brukskonto | 76 246,32 kr |
| Sparekonto Pluss | NO5586010000027 | Sparekonto | 125 000,00 kr |

**Kari Hansen:**
| Konto | IBAN | Type | Saldo |
|-------|------|------|-------|
| Brukskonto | NO3386010000035 | Brukskonto | 90 688,21 kr |

---

## Trollbanken (`trollbank`)

| Navn | Personnummer | Keycloak-bruker | Passord |
|------|-------------|-----------------|---------|
| Per Olsen | 12068712345 | per@trollbank | demo |
| Ingrid Berg | 22049156789 | ingrid@trollbank | demo |
| Lars Svendsen | 05079834567 | lars@trollbank | demo |

### Kontoer

**Per Olsen:**
| Konto | IBAN | Type | Saldo |
|-------|------|------|-------|
| Hverdagskonto | NO1986020000017 | Brukskonto | 96 715,19 kr |
| Trollspar | NO9486020000025 | Sparekonto | 89 500,00 kr |

**Ingrid Berg:**
| Konto | IBAN | Type | Saldo |
|-------|------|------|-------|
| Hverdagskonto | NO7286020000033 | Brukskonto | 87 099,44 kr |
| Trollspar | NO5086020000041 | Sparekonto | 215 000,00 kr |

**Lars Svendsen:**
| Konto | IBAN | Type | Saldo |
|-------|------|------|-------|
| Ungdomskonto | NO9886020000050 | Brukskonto | 93 847,02 kr |

---

## Nordlys Sparebank (`nordlys`)

| Navn | Personnummer | Keycloak-bruker | Passord |
|------|-------------|-----------------|---------|
| Astrid Johansen | 30119067890 | astrid@nordlys | demo |
| Erik Haugen | 18057523456 | erik@nordlys | demo |
| Solveig Dahl | 09028498765 | solveig@nordlys | demo |

### Kontoer

**Astrid Johansen:**
| Konto | IBAN | Type | Saldo |
|-------|------|------|-------|
| Dagligkonto | NO5886030000015 | Brukskonto | 72 025,78 kr |
| Nordlys Sparekonto | NO3686030000023 | Sparekonto | 156 000,00 kr |
| Hoyrentekonto | NO1486030000031 | Sparekonto | 320 000,00 kr |

**Erik Haugen:**
| Konto | IBAN | Type | Saldo |
|-------|------|------|-------|
| Dagligkonto | NO6286030000040 | Brukskonto | 74 259,93 kr |
| Hoyrentekonto | NO6186030000058 | Sparekonto | 75 000,00 kr |

**Solveig Dahl:**
| Konto | IBAN | Type | Saldo |
|-------|------|------|-------|
| Dagligkonto | NO3986030000066 | Brukskonto | 73 108,75 kr |

---

## Innlogging

1. Gå til http://localhost:3100
2. Velg bank
3. Skriv inn personnummer og klikk "Logg inn"

Alle brukere har Keycloak-konto og kan logge inn med personnummer.

## Tjenester

| Tjeneste | Port |
|----------|------|
| Frontend (nettbank) | 3100 |
| BFF | 8085 |
| Core | 8086 |
| Clearing | 8087 |
| Admin | 8088 |
| KodaStore | 8080 |
| Keycloak | 8180 |

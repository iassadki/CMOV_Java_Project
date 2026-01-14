// ===== Sensor =====
const int PIN_MB1010_AN = A0;

const int OCCUPIED_ENTER_CM = 53;
const int FREE_EXIT_CM      = 54;
const int STABLE_COUNT      = 3;

// 0 = only on change, 2000 = debug every 2s
const unsigned long HEARTBEAT_MS = 0;

// ===== RGB module pins (V/G R G B) =====
const int PIN_R = 11;
const int PIN_G = 10;
const int PIN_B = 9;

// If V/G is connected to +5V => common anode => true
// If V/G is connected to GND  => common cathode => false
const bool COMMON_ANODE = true;

// ===== Manual override via Serial =====
bool manualMode = false;   // quando true, o LED é controlado por comandos
// LED_ON  => azul fixo (manual)
// LED_OFF => volta ao modo automático (ocupado/vermelho, livre/verde)

void setRGB(uint8_t r, uint8_t g, uint8_t b) {
  if (COMMON_ANODE) {
    analogWrite(PIN_R, 255 - r);
    analogWrite(PIN_G, 255 - g);
    analogWrite(PIN_B, 255 - b);
  } else {
    analogWrite(PIN_R, r);
    analogWrite(PIN_G, g);
    analogWrite(PIN_B, b);
  }
}

void updateLed(bool occ) {
  if (occ) {
    // occupied => RED
    setRGB(255, 0, 0);
  } else {
    // free => GREEN
    setRGB(0, 255, 0);
  }
}

// ===== Utils =====
int cmFromAnalog(int adc) {
  return (int)(adc * 2.54);  // adc ≈ inches (your calibration)
}

int median5(int a[]) {
  for (int i=0;i<4;i++) for (int j=i+1;j<5;j++) if (a[j] < a[i]) {
    int t=a[i]; a[i]=a[j]; a[j]=t;
  }
  return a[2];
}

// ===== State =====
bool occupied = false;
bool candidate = false;
int stable = 0;
unsigned long lastSent = 0;

// ---- serial command handling ----
void handleCommand(String cmdRaw) {
  cmdRaw.trim();

  if (cmdRaw == "LED_ON") {
    manualMode = true;
    setRGB(0, 0, 255); // azul
    Serial.println("ACK:LED_ON;");
  } 
  else if (cmdRaw == "LED_OFF") {
    manualMode = false;
    updateLed(occupied); // volta ao modo automático
    Serial.println("ACK:LED_OFF;");
  }
  else {
    // comando desconhecido (opcional)
    Serial.print("ACK:UNKNOWN:");
    Serial.print(cmdRaw);
    Serial.println(";");
  }
}

void sendState(bool occ, int cm) {
  Serial.print("spot1=");
  Serial.print(occ ? 1 : 0);
  Serial.print(",cm=");
  Serial.print(cm);
  Serial.println(";");
  lastSent = millis();
}

void setup() {
  Serial.begin(9600);
  Serial.setTimeout(10); // evita bloqueios longos no readStringUntil
  delay(200);

  pinMode(PIN_R, OUTPUT);
  pinMode(PIN_G, OUTPUT);
  pinMode(PIN_B, OUTPUT);

  // Boot indication: blue flash
  setRGB(0, 0, 255);
  delay(250);

  updateLed(occupied);
  Serial.println("BOOT;");
}

void loop() {
  // 1) Ler comandos vindos do Java (via Serial)
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    if (cmd.length() > 0) handleCommand(cmd);
  }

  // 2) Ler sensor MB1010
  int v[5];
  for (int i=0;i<5;i++){ v[i]=analogRead(PIN_MB1010_AN); delay(10); }
  int adc = median5(v);
  int cm  = cmFromAnalog(adc);

  bool instant = occupied;
  if (!occupied) {
    if (cm > 0 && cm < OCCUPIED_ENTER_CM) instant = true;
  } else {
    if (cm > FREE_EXIT_CM) instant = false;
  }

  if (instant != candidate) { candidate = instant; stable = 1; }
  else { stable++; }

  if (candidate != occupied && stable >= STABLE_COUNT) {
    occupied = candidate;

    // Só atualiza LED automaticamente se NÃO estiver em modo manual
    if (!manualMode) {
      updateLed(occupied);
    }

    sendState(occupied, cm);   // envia apenas quando muda
  }

  if (HEARTBEAT_MS > 0 && millis() - lastSent > HEARTBEAT_MS) {
    sendState(occupied, cm);   // debug heartbeat
  }

  delay(200);
}

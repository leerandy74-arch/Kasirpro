const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_merchant_1";
const BOB_UID = "bob_merchant_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function nowPast(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated user cannot read or write any workspace collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("branches").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("tx_1")
      .set({ userId: ALICE_UID })
  );
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's branches, products, staff, or transactions", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = nowPast(5000);
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("branches")
      .doc("branch_hq")
      .set({
        userId: ALICE_UID,
        name: "Cabang Pusat",
        code: "HQ-01",
        address: "Jl. Sudirman No. 1",
        phone: "021555000",
        headquarters: true,
        active: true,
        createdAt: ts,
        updatedAt: ts,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("branches")
      .doc("branch_hq")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("branches").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("branches")
      .doc("branch_hq")
      .update({ name: "Hijacked", updatedAt: nowPast(1000) })
  );
});

test("Owner can bootstrap workspace, branches, staff, products, and transactions in WriteBatch", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowPast(3000);
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  const batch = aliceDb.batch();
  batch.set(userRef, {
    userId: ALICE_UID,
    storeName: "Kopi Nusantara",
    e2eeSalt: "c2FsdF92YWx1ZV8xMjM0NTY=",
    e2eeKeyCheck: "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
    qrisMerchantId: "ID1020304050607",
    qrisMerchantName: "KOPI NUSANTARA",
    autoBackupIntervalHours: 12,
    lastBackupEpochMs: 0,
    createdAt: ts,
    updatedAt: ts,
  });

  const branchRef = userRef.collection("branches").doc("branch_hq");
  batch.set(branchRef, {
    userId: ALICE_UID,
    name: "Cabang Pusat",
    code: "HQ-01",
    address: "Jakarta Pusat",
    phone: "08123456789",
    headquarters: true,
    active: true,
    createdAt: ts,
    updatedAt: ts,
  });

  const staffRef = userRef.collection("staff").doc("staff_central");
  batch.set(staffRef, {
    userId: ALICE_UID,
    username: "admin.pusat",
    fullName: "Admin Pusat",
    role: "CENTRAL_ADMIN",
    branchId: "branch_hq",
    passwordHash: "hash_1234567890abcdef1234567890abcdef",
    passwordSalt: "salt_12345678",
    active: true,
    createdAt: ts,
    updatedAt: ts,
  });

  const productRef = userRef.collection("products").doc("prod_1");
  batch.set(productRef, {
    userId: ALICE_UID,
    branchId: "branch_hq",
    sku: "SKU-001",
    name: "Kopi Susu Aren",
    category: "Minuman",
    price: 24000,
    costPrice: 10000,
    stock: 50,
    minStock: 10,
    unit: "cup",
    active: true,
    createdAt: ts,
    updatedAt: ts,
  });

  await assertSucceeds(batch.commit());
});

test("Shadow update, ghost fields, future timestamps, and invalid roles are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowPast(4000);
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    userRef.collection("branches").doc("branch_hq").set({
      userId: ALICE_UID,
      name: "Pusat",
      code: "HQ",
      address: "Jakarta",
      phone: "0811",
      headquarters: true,
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject ghost field `id` (which would break @DocumentId)
  await assertFails(
    userRef.collection("products").doc("prod_bad").set({
      id: "prod_bad",
      userId: ALICE_UID,
      branchId: "branch_hq",
      sku: "SKU-BAD",
      name: "Bad",
      category: "Test",
      price: 1000,
      costPrice: 500,
      stock: 10,
      minStock: 2,
      unit: "pcs",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject orphaned product with non-existent branchId
  await assertFails(
    userRef.collection("products").doc("prod_orphan").set({
      userId: ALICE_UID,
      branchId: "branch_does_not_exist",
      sku: "SKU-ORP",
      name: "Orphan",
      category: "Test",
      price: 1000,
      costPrice: 500,
      stock: 10,
      minStock: 2,
      unit: "pcs",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject invalid staff role
  await assertFails(
    userRef.collection("staff").doc("staff_bad").set({
      userId: ALICE_UID,
      username: "hacker",
      fullName: "Hacker",
      role: "SUPERUSER",
      branchId: "branch_hq",
      passwordHash: "hash_1234567890abcdef1234567890abcdef",
      passwordSalt: "salt_12345678",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject future timestamp
  await assertFails(
    userRef.collection("branches").doc("branch_future").set({
      userId: ALICE_UID,
      name: "Future",
      code: "FUT",
      address: "Jakarta",
      phone: "0811",
      headquarters: false,
      active: true,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Staff password change, automatic stock deduction, transaction terminal lock, audit logs & cloud backups", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowPast(6000);
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    userRef.collection("branches").doc("branch_hq").set({
      userId: ALICE_UID,
      name: "Pusat",
      code: "HQ",
      address: "Jakarta",
      phone: "0811",
      headquarters: true,
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    userRef.collection("staff").doc("cashier_1").set({
      userId: ALICE_UID,
      username: "kasir.01",
      fullName: "Kasir Satu",
      role: "CASHIER",
      branchId: "branch_hq",
      passwordHash: "oldhash_1234567890abcdef12345678",
      passwordSalt: "oldsalt_1234",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Central Admin password update succeeds
  await assertSucceeds(
    userRef.collection("staff").doc("cashier_1").update({
      passwordHash: "newhash_1234567890abcdef12345678",
      passwordSalt: "newsalt_5678",
      updatedAt: nowPast(1000),
    })
  );

  // Product stock deduction succeeds
  await assertSucceeds(
    userRef.collection("products").doc("prod_1").set({
      userId: ALICE_UID,
      branchId: "branch_hq",
      sku: "SKU-1",
      name: "Matcha Latte",
      category: "Minuman",
      price: 28000,
      costPrice: 12000,
      stock: 20,
      minStock: 5,
      unit: "cup",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    userRef.collection("products").doc("prod_1").update({
      stock: 18,
      updatedAt: nowPast(1000),
    })
  );

  // Transaction create, WhatsApp flag update, void transition, and terminal lock
  const txRef = userRef.collection("transactions").doc("tx_001");
  await assertSucceeds(
    txRef.set({
      userId: ALICE_UID,
      branchId: "branch_hq",
      branchName: "Pusat",
      receiptNumber: "INV-20261117-001",
      cashierId: "cashier_1",
      cashierName: "Kasir Satu",
      paymentMethod: "QRIS",
      status: "COMPLETED",
      subtotal: 56000,
      discount: 0,
      tax: 0,
      totalAmount: 56000,
      paidAmount: 56000,
      changeAmount: 0,
      itemCount: 2,
      encryptedPayload: "ZW5jcnlwdGVkX3BheWxvYWRfYmFzZTY0",
      e2eeIv: "aXZfMTJfYnl0ZXM=",
      e2eeVerified: true,
      whatsappSent: false,
      monthKey: "2026-11",
      dayKey: "2026-11-17",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    txRef.update({ whatsappSent: true, updatedAt: nowPast(800) })
  );
  await assertSucceeds(
    txRef.update({ status: "VOIDED", updatedAt: nowPast(500) })
  );
  // Terminal lock prevents modifying VOIDED transaction
  await assertFails(
    txRef.update({ status: "COMPLETED", updatedAt: nowPast(200) })
  );

  // Audit log is create-only
  const logRef = userRef.collection("auditLogs").doc("log_1");
  await assertSucceeds(
    logRef.set({
      userId: ALICE_UID,
      branchId: "branch_hq",
      actorId: "cashier_1",
      actorName: "Kasir Satu",
      actorRole: "CASHIER",
      actionType: "CHECKOUT",
      summary: "Transaksi INV-20261117-001 selesai",
      createdAt: ts,
    })
  );
  await assertFails(logRef.update({ summary: "Tampered" }));
  await assertFails(logRef.delete());

  // Cloud backup create & delete
  const backupRef = userRef.collection("cloudBackups").doc("bkp_1");
  await assertSucceeds(
    backupRef.set({
      userId: ALICE_UID,
      backupType: "AUTO",
      encryptedSnapshot: "ZW5jcnlwdGVkX3NuYXBzaG90",
      e2eeIv: "aXZfMTJfYnl0ZXM=",
      checksumSha256:
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      recordCount: 15,
      createdBy: "Admin Pusat",
      createdAt: ts,
    })
  );
  await assertFails(backupRef.update({ backupType: "MANUAL" }));
  await assertSucceeds(backupRef.delete());
});

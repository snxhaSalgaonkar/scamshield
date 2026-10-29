-- TEST FIXTURE ONLY (npm run test:sql): demo reports used to exercise the triggers, RLS and RPCs
-- in an in-process Postgres. Never run this against the real Supabase project.
-- Re-runnable: removes its own earlier rows (all fixture devices start with '5eed').
-- What it creates:
--   • number_series: ~80 common 4-digit prefixes → operator + telecom circle.
--     APPROXIMATE (legacy allocation; mobile number portability makes it imperfect).
--   • 45 reports over the last 7 days from 15 fake devices, all scam types, 15 circles.
--   • Cluster 98112-XXXXX: 7 reports, 4 distinct numbers (wrong-number / OTP gang, Delhi).
--   • Cluster 70421-XXXXX: 5 reports, 3 distinct numbers (digital-arrest gang).
--   • Demo Scenario B number +919811255501 reported by exactly 2 devices
--     (the live demo phone adds the 3rd → becomes flagged).
--   • 3 reports with status 'false_positive' and 2 with user_feedback 'safe' (FPR card ≠ 0).
--   • Flagged numbers: +918448312276 (4 devices), +919423556677 (3 devices).

-- ===================== number_series =====================
insert into public.number_series (prefix, operator, telecom_circle) values
  ('9810','Airtel','Delhi'), ('9811','Vi','Delhi'), ('9818','Airtel','Delhi'), ('9871','Airtel','Delhi'),
  ('9899','Vi','Delhi'), ('9910','Airtel','Delhi'), ('8448','Airtel','Delhi'), ('7428','Airtel','Delhi'),
  ('8860','Airtel','Delhi'), ('8595','Airtel','Delhi'), ('7011','Jio','Delhi'),
  ('9820','Vi','Mumbai'), ('9821','Vi','Mumbai'), ('9833','Vi','Mumbai'), ('9892','Airtel','Mumbai'),
  ('9930','Vi','Mumbai'), ('9167','Vi','Mumbai'), ('7738','Airtel','Mumbai'), ('9020','Jio','Mumbai'),
  ('9822','Vi','Maharashtra'), ('9890','Vi','Maharashtra'), ('9850','Airtel','Maharashtra'), ('9423','BSNL','Maharashtra'),
  ('9840','Airtel','Tamil Nadu'), ('9884','Vi','Tamil Nadu'), ('9841','Airtel','Tamil Nadu'), ('9443','BSNL','Tamil Nadu'),
  ('9880','Airtel','Karnataka'), ('9886','Vi','Karnataka'), ('9900','Airtel','Karnataka'), ('9845','Airtel','Karnataka'),
  ('9831','Airtel','Kolkata'), ('9836','Vi','Kolkata'), ('9903','Airtel','Kolkata'), ('9830','Vi','Kolkata'),
  ('8100','Airtel','Kolkata'), ('8420','Airtel','Kolkata'),
  ('9849','Airtel','Andhra Pradesh'), ('9848','Vi','Andhra Pradesh'), ('9866','Airtel','Andhra Pradesh'),
  ('9000','Airtel','Andhra Pradesh'), ('8886','Airtel','Andhra Pradesh'),
  ('9824','Vi','Gujarat'), ('9825','Vi','Gujarat'), ('9879','Vi','Gujarat'),
  ('9828','Vi','Rajasthan'), ('9829','Airtel','Rajasthan'), ('9414','BSNL','Rajasthan'),
  ('9415','BSNL','Uttar Pradesh (East)'), ('9839','Vi','Uttar Pradesh (East)'), ('9450','BSNL','Uttar Pradesh (East)'),
  ('9412','BSNL','Uttar Pradesh (West)'), ('9837','Vi','Uttar Pradesh (West)'), ('9411','BSNL','Uttar Pradesh (West)'),
  ('7042','Airtel','Uttar Pradesh (West)'),
  ('9431','BSNL','Bihar'), ('9835','Airtel','Bihar'), ('9934','Airtel','Bihar'), ('6203','Jio','Bihar'), ('9905','Airtel','Bihar'),
  ('9437','BSNL','Odisha'), ('9861','Airtel','Odisha'),
  ('9846','Vi','Kerala'), ('9847','Airtel','Kerala'), ('9895','Airtel','Kerala'),
  ('9815','Airtel','Punjab'), ('9814','Vi','Punjab'), ('9876','Airtel','Punjab'),
  ('9416','BSNL','Haryana'), ('9812','Vi','Haryana'), ('9354','Airtel','Haryana'),
  ('9425','BSNL','Madhya Pradesh'), ('9826','Airtel','Madhya Pradesh'),
  ('9435','BSNL','Assam'), ('9854','Airtel','Assam'),
  ('9419','BSNL','Jammu & Kashmir'), ('9418','BSNL','Himachal Pradesh'), ('9436','BSNL','North East'),
  ('9434','BSNL','West Bengal'), ('9832','Airtel','West Bengal'), ('7595','Airtel','West Bengal'), ('7890','Airtel','West Bengal')
on conflict (prefix) do update set operator = excluded.operator, telecom_circle = excluded.telecom_circle;

-- ===================== clear previous seed reports =====================
do $$
declare
  affected text[];
  n text;
begin
  select coalesce(array_agg(distinct caller_number), '{}') into affected
  from public.reports where device_id like '5eed%';

  delete from public.reports where device_id like '5eed%';
  delete from public.numbers where number = any (affected);
  foreach n in array affected loop
    perform public.refresh_number(n);   -- rebuilds the row if real (non-seed) reports exist
  end loop;
end;
$$;

-- ===================== reports =====================
-- number_prefix and telecom_circle are left empty on purpose: the BEFORE INSERT
-- trigger fills them from caller_number + number_series (same path as a phone that omits them).
insert into public.reports (
  device_id, caller_number, number_prefix, telecom_circle,
  risk_score, l1_score, l2_score, l3_score, scam_type,
  matched_phrases, reasons, similarity, detected_via,
  action_taken, user_feedback, status, call_started_at, created_at
)
select
  '5eed0000-0000-4000-8000-0000000000' || lpad(s.dev::text, 2, '0'),
  s.num, '', null,
  s.risk, s.l1, s.l2, s.l3, s.type,
  s.phrases::jsonb, s.reasons::jsonb, s.sim::jsonb, s.via::text[],
  s.action, s.feedback, s.status,
  now() - (s.hrs * interval '1 hour') - interval '4 minutes',
  now() - (s.hrs * interval '1 hour')
from (values
  -- dev, number, risk, l1, l2, l3, scam_type, matched_phrases, reasons, similarity, detected_via, action, feedback, status, hours_ago
  -- Flagged digital-arrest number (4 devices)
  (1, '+918448312276', 90, 92, 45, 10, 'digital_arrest',
     '["CBI officer","digital arrest","don''t disconnect the call","money laundering case"]',
     '["Authority impersonation (CBI)","Isolation demand: stay on the video call","Asked to move money for ''verification''"]',
     '{"score":0.88,"matched":"you are under digital arrest"}', '{voice}', 'disconnected', null, 'escalated', 150),
  (2, '+918448312276', 90, 88, 60, 20, 'digital_arrest',
     '["parcel seized by customs","drugs found in your parcel","don''t tell your family"]',
     '["Authority impersonation (customs)","Secrecy demand","Number reported by 1 other device"]',
     '{"score":0.91,"matched":"drugs found in your parcel"}', '{voice,reputation}', 'disconnected', null, 'reviewed', 120),
  (3, '+918448312276', 90, 90, 60, 20, 'digital_arrest',
     '["arrest warrant","video call with police","don''t disconnect the call"]',
     '["Authority impersonation (police)","Isolation demand","Number reported by 2 other devices"]',
     '{"score":0.84,"matched":"an arrest warrant has been issued in your name"}', '{voice,reputation}', 'disconnected', null, 'new', 70),
  (4, '+918448312276', 90, 90, 75, 0, 'digital_arrest',
     '["CBI officer","Aadhaar linked to crime"]',
     '["Authority impersonation (CBI)","Number reported by 3 other devices"]',
     '{"score":0.82,"matched":"your aadhaar is linked to a crime"}', '{voice,reputation}', 'disconnected', null, 'new', 30),

  -- Cluster 70421-XXXXX (digital arrest, 3 numbers)
  (5, '+917042110023', 90, 90, 30, 45, 'digital_arrest',
     '["narcotics bureau","digital arrest","transfer to RBI verification account"]',
     '["Authority impersonation (NCB)","Money transfer demand","UPI app opened during call"]',
     '{"score":0.86,"matched":"transfer the money to the RBI verification account"}', '{voice,correlation}', 'disconnected', null, 'reviewed', 140),
  (6, '+917042110023', 90, 85, 50, 0, 'digital_arrest',
     '["mumbai police","case registered against you","stay on video call"]',
     '["Authority impersonation (police)","Isolation demand"]',
     '{"score":0.8,"matched":"a case has been registered against you"}', '{voice,reputation}', 'disconnected', null, 'new', 100),
  (7, '+917042136781', 75, 84, 20, 0, 'digital_arrest',
     '["CBI officer","don''t tell anyone"]',
     '["Authority impersonation (CBI)","Secrecy demand"]',
     '{"score":0.79,"matched":"do not tell anyone about this investigation"}', '{voice}', 'disconnected', null, 'new', 60),
  (8, '+917042158890', 90, 93, 40, 15, 'digital_arrest',
     '["digital arrest","money laundering case","don''t disconnect the call"]',
     '["Authority impersonation","Isolation demand","Money transfer demand"]',
     '{"score":0.9,"matched":"you are under digital arrest"}', '{voice,reputation}', 'disconnected', null, 'new', 20),
  (9, '+917042136781', 75, 86, 35, 30, 'digital_arrest',
     '["cyber crime department","your SIM will be blocked"]',
     '["Authority impersonation","Urgency: action within 2 hours"]',
     '{"score":0.74,"matched":"your sim card will be blocked in two hours"}', '{voice}', 'countdown_cancelled', null, 'new', 5),

  -- Cluster 98112-XXXXX (wrong-number OTP gang, 4 numbers). +919811255501 = Scenario B (2 devices).
  (10, '+919811255501', 90, 5, 15, 95, 'wrong_number_distraction', '[]',
     '["Bank OTP SMS received during call","Caller kept victim talking: ''sorry, wrong number''"]',
     null, '{sms,correlation}', 'disconnected', null, 'new', 50),
  (11, '+919811255501', 90, 0, 40, 95, 'wrong_number_distraction', '[]',
     '["Bank OTP SMS received during call","Number-series cluster 98112 active"]',
     null, '{sms,correlation}', 'disconnected', null, 'reviewed', 26),
  (12, '+919811200417', 80, 0, 15, 90, 'otp_theft', '[]',
     '["OTP SMS received during unknown call"]',
     null, '{sms,correlation}', 'disconnected', null, 'new', 90),
  (13, '+919811200417', 90, 10, 30, 96, 'otp_theft', '[]',
     '["OTP SMS received during call","Primer SMS 2 min before call"]',
     null, '{sms,correlation}', 'disconnected', null, 'new', 44),
  (14, '+919811231908', 80, 0, 20, 92, 'otp_theft', '[]',
     '["OTP SMS received during unknown call"]',
     null, '{sms,correlation}', 'disconnected', null, 'new', 12),
  (15, '+919811276642', 90, 20, 15, 95, 'wrong_number_distraction', '[]',
     '["OTP SMS received during call","Debit SMS 40 s later"]',
     null, '{sms,correlation}', 'disconnected', null, 'new', 8),
  (1, '+919811276642', 80, 0, 10, 90, 'otp_theft', '[]',
     '["OTP SMS received during unknown call"]',
     null, '{sms,correlation}', 'disconnected', null, 'new', 3),

  -- KYC / bank impersonation
  (2, '+917428390011', 90, 82, 35, 60, 'kyc_bank_impersonation',
     '["your KYC has expired","account will be blocked today","share the OTP to verify"]',
     '["Bank impersonation","Asked for OTP","Urgency: account blocked today"]',
     '{"score":0.81,"matched":"your kyc is pending, account will be blocked"}', '{voice,sms,correlation}', 'disconnected', null, 'reviewed', 130),
  (3, '+917428390011', 59, 78, 30, 50, 'kyc_bank_impersonation',
     '["update your KYC","PAN card details"]',
     '["Bank impersonation","Asked for PAN details"]',
     '{"score":0.72,"matched":"update your kyc details now"}', '{voice}', 'warn', null, 'new', 80),
  (6, '+918860214455', 75, 80, 10, 20, 'kyc_bank_impersonation',
     '["calling from SBI head office","card will be blocked"]',
     '["Bank impersonation","Urgency: card blocked"]',
     '{"score":0.77,"matched":"your debit card will be blocked today"}', '{voice}', 'countdown_cancelled', null, 'new', 36),
  (7, '+919354778120', 90, 82, 20, 90, 'kyc_bank_impersonation',
     '["read me the OTP","KYC verification"]',
     '["Asked to read out OTP","OTP SMS received during call"]',
     '{"score":0.89,"matched":"read me the otp you just received"}', '{voice,sms,correlation}', 'disconnected', null, 'new', 16),

  -- Remote access
  (8, '+918595012345', 90, 72, 25, 95, 'remote_access',
     '["install AnyDesk","tell me the code on your screen"]',
     '["Remote-access app opened during call","Screen-share code requested"]',
     '{"score":0.84,"matched":"install anydesk so i can help you"}', '{voice,correlation}', 'disconnected', null, 'escalated', 110),
  (9, '+918595012345', 90, 65, 50, 92, 'remote_access',
     '["download this app for refund"]',
     '["Remote-access app opened during call","Number reported by 1 other device"]',
     '{"score":0.78,"matched":"download this app to process your refund"}', '{voice,correlation,reputation}', 'disconnected', null, 'new', 40),
  (10, '+917011456789', 80, 0, 10, 90, 'remote_access', '[]',
     '["Remote-access app (QuickSupport) opened during unknown call"]',
     null, '{correlation}', 'disconnected', null, 'new', 6),

  -- Refund / lottery / QR
  (11, '+916203987654', 47, 65, 20, 40, 'refund_lottery_qr',
     '["scan this QR to receive refund","you have won a lottery"]',
     '["Refund-by-QR script"]',
     '{"score":0.76,"matched":"scan this qr code to receive your refund"}', '{voice}', 'warn', null, 'reviewed', 125),
  (12, '+916203987654', 52, 75, 40, 30, 'refund_lottery_qr',
     '["KBC lottery winner","pay processing fee"]',
     '["Lottery script","Upfront fee demand"]',
     '{"score":0.71,"matched":"you have won kbc lottery, pay the processing fee"}', '{voice}', 'warn', null, 'new', 75),
  (13, '+919905112233', 90, 84, 45, 70, 'refund_lottery_qr',
     '["enter your UPI PIN to receive money"]',
     '["UPI collect request during call","Asked for UPI PIN"]',
     '{"score":0.87,"matched":"enter your upi pin to receive the money"}', '{voice,correlation}', 'disconnected', null, 'new', 22),

  -- Utility disconnection
  (14, '+918100554433', 90, 82, 40, 30, 'utility_disconnection',
     '["your electricity will be disconnected tonight","pay the pending bill now"]',
     '["Utility-disconnection script","Urgency: tonight"]',
     '{"score":0.85,"matched":"your electricity connection will be cut tonight"}', '{voice,reputation}', 'disconnected', null, 'new', 100),
  (15, '+918100554433', 75, 81, 30, 0, 'utility_disconnection',
     '["electricity bill not updated","call this officer"]',
     '["Utility-disconnection script"]',
     '{"score":0.73,"matched":"your electricity bill is not updated"}', '{voice}', 'countdown_cancelled', null, 'new', 55),
  (4, '+917595321100', 52, 70, 30, 40, 'utility_disconnection',
     '["gas connection will be stopped"]',
     '["Utility-disconnection script"]',
     '{"score":0.66,"matched":"your gas connection will be stopped"}', '{voice}', 'warn', null, 'new', 14),

  -- Voice-clone / family emergency
  (5, '+918420998877', 55, 70, 10, 60, 'voice_clone_impersonation',
     '["mummy I''m in trouble","don''t tell papa","send money urgently"]',
     '["Family-emergency script","Urgent money request"]',
     '{"score":0.69,"matched":"i am in trouble please send money urgently"}', '{voice,correlation}', 'warn', null, 'new', 10),
  (6, '+918420998877', 90, 60, 30, 90, 'voice_clone_impersonation',
     '["accident","send money to this UPI"]',
     '["Family-emergency script","UPI app opened during call"]',
     '{"score":0.74,"matched":"there has been an accident, send money now"}', '{voice,correlation}', 'disconnected', null, 'new', 2),

  -- False positives (officer-reviewed)
  (7, '+919000112233', 40, 30, 40, 30, 'other', '[]',
     '["Unknown caller","Long call"]',
     null, '{reputation}', 'warn', null, 'false_positive', 145),
  (8, '+917890123456', 47, 60, 10, 50, 'kyc_bank_impersonation',
     '["bank verification"]',
     '["Bank keyword during unknown call"]',
     '{"score":0.55,"matched":"this is a call from your bank"}', '{voice}', 'warn', null, 'false_positive', 95),
  (9, '+919845000111', 80, 0, 10, 90, 'otp_theft', '[]',
     '["OTP SMS received during unknown call"]',
     null, '{sms,correlation}', 'disconnected', null, 'false_positive', 118),

  -- Citizen-marked safe (delivery OTP, Scenario C pattern)
  (10, '+919167001234', 40, 0, 25, 85, 'otp_theft', '[]',
     '["OTP SMS during call (''share with delivery agent'')"]',
     null, '{sms,correlation}', 'warn', 'safe', 'new', 66),
  (11, '+919167001234', 40, 0, 25, 85, 'otp_theft', '[]',
     '["OTP SMS during call (''share with delivery agent'')"]',
     null, '{sms,correlation}', 'warn', 'safe', 'new', 34),

  -- Spread across circles; +919423556677 flagged (3 devices)
  (12, '+919423556677', 90, 91, 50, 20, 'digital_arrest',
     '["CBI officer","digital arrest"]',
     '["Authority impersonation (CBI)"]',
     '{"score":0.9,"matched":"you are under digital arrest"}', '{voice,reputation}', 'disconnected', null, 'new', 18),
  (13, '+919423556677', 90, 89, 55, 0, 'digital_arrest',
     '["parcel with drugs","don''t disconnect"]',
     '["Authority impersonation (customs)","Number reported by 1 other device"]',
     '{"score":0.87,"matched":"drugs found in your parcel"}', '{voice,reputation}', 'disconnected', null, 'new', 9),
  (14, '+919423556677', 90, 90, 70, 0, 'digital_arrest',
     '["digital arrest","video call"]',
     '["Number reported by 2 other devices"]',
     '{"score":0.88,"matched":"you are under digital arrest"}', '{voice,reputation}', 'disconnected', null, 'new', 4),
  (15, '+918886234567', 80, 0, 20, 91, 'otp_theft', '[]',
     '["OTP SMS received during unknown call"]',
     null, '{sms,correlation}', 'disconnected', null, 'new', 28),
  (1, '+919443221100', 75, 80, 30, 10, 'kyc_bank_impersonation',
     '["KYC update","account freeze"]',
     '["Bank impersonation"]',
     '{"score":0.75,"matched":"your account will be frozen"}', '{voice}', 'countdown_cancelled', null, 'reviewed', 85),
  (2, '+919830776655', 90, 95, 30, 40, 'digital_arrest',
     '["ED officer","money laundering case","don''t tell your family"]',
     '["Authority impersonation (ED)","Secrecy demand","Money transfer demand"]',
     '{"score":0.92,"matched":"you are involved in a money laundering case"}', '{voice,correlation}', 'disconnected', null, 'new', 46),
  (3, '+919876509876', 47, 65, 20, 40, 'refund_lottery_qr',
     '["cashback refund","scan QR"]',
     '["Refund-by-QR script"]',
     '{"score":0.7,"matched":"scan this qr code to receive your refund"}', '{voice}', 'warn', null, 'new', 1.5),
  (4, '+917738445566', 90, 85, 45, 60, 'kyc_bank_impersonation',
     '["share the OTP","KYC expired"]',
     '["Asked for OTP","OTP SMS received during call"]',
     '{"score":0.86,"matched":"share the otp you received"}', '{voice,sms,correlation}', 'disconnected', null, 'new', 0.8),
  (5, '+919020331122', 90, 0, 35, 96, 'wrong_number_distraction', '[]',
     '["OTP SMS received during call","Caller claimed wrong number"]',
     null, '{sms,correlation}', 'disconnected', null, 'new', 7)
) as s(dev, num, risk, l1, l2, l3, type, phrases, reasons, sim, via, action, feedback, status, hrs);

-- Quick check (should show 45 seed reports and the two flagged numbers):
-- select count(*) from reports where device_id like '5eed%';
-- select number, reporter_count, safe_count, is_flagged from numbers order by reporter_count desc;

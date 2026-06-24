# Patch: HttpAdapter request capture + redirect

Target: `overdrive-decompiled/smali/com/anki/util/http/HttpAdapter.smali`, method
`startRequest(JLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[BILjava/lang/String;I)V`.

This is the single chokepoint for every cloud request the native engine makes (confirmed via
Ghidra: `RushHour::Init(... IHttpAdapter*)` delegates all HTTP to this Java class). Params:
`p3 = uri (String)`, `p4 = headers (String[])`, `p5 = params (String[])`, `p6 = body (byte[])`,
`p7 = httpMethod (int)`. Method is `.locals 17`, so `v0`/`v1` are free scratch at the prologue
(no local is live until `v14` is created at `.line 60`).

Apply in two stages.

## Stage A2 — capture only (logging to logcat tag `OD-HTTP`)

Insert immediately after the `.prologue` line (before `.line 60` / the `new-instance v14`):

```smali
    .prologue
    # === OD capture: log uri + method + body to logcat (tag OD-HTTP) ===
    const-string v0, "OD-HTTP"
    invoke-static {v0, p3}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    new-instance v0, Ljava/lang/StringBuilder;
    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
    const-string v1, "method="
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    move-result-object v0
    invoke-virtual {v0, p7}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    move-result-object v0
    const-string v1, " body="
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    move-result-object v0
    if-eqz p6, :od_no_body
    new-instance v1, Ljava/lang/String;
    invoke-direct {v1, p6}, Ljava/lang/String;-><init>([B)V
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    move-result-object v0
    :od_no_body
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v1
    const-string v0, "OD-HTTP"
    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    # === end OD capture ===
    .line 60
    new-instance v14, Ljava/lang/StringBuilder;
```

Optionally also log the headers (they include `Authorization` + `Anki-App-Key`). Insert at the
`:cond_0` label (the headers `StringBuilder` `v14` is fully built there, before the runnable):

```smali
    :cond_0
    # === OD capture: log headers ===
    invoke-virtual {v14}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v0
    const-string v1, "OD-HTTP"
    invoke-static {v1, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    # === end ===
    move-object/from16 v0, p0
```

Read it on device with: `adb logcat -s OD-HTTP`.

## Stage A1 — redirect hosts to our server

Add BEFORE `.line 60` (after the capture block). Replace `OURHOST:8080` with the server's LAN
address. `String.replace(CharSequence,CharSequence)` replaces all occurrences literally; p3 is
reassigned in place.

```smali
    # === OD redirect: point *.api.anki.com at our server ===
    const-string v0, "https://accounts.api.anki.com/1/"
    const-string v1, "http://OURHOST:8080/accounts/1/"
    invoke-virtual {p3, v0, v1}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
    move-result-object p3
    const-string v0, "https://ankival.api.anki.com/1/"
    const-string v1, "http://OURHOST:8080/ankival/1/"
    invoke-virtual {p3, v0, v1}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
    move-result-object p3
    # (repeat for storegate/virtualrewards when those are needed)
    # === end OD redirect ===
```

Notes:
- Capture (A2) needs only the logging block — requests still fail against the dead hosts, but we
  see the outgoing shapes first. Add the redirect (A1) once the server is up.
- TLS: with the redirect pointing at `http://` on a trusted LAN there is no cert work. If you
  prefer https, give the server a cert the device trusts (old `targetSdk` trusts user CAs).
- After patching: `apktool b`, `zipalign`, `apksigner` (see PLAN.md Phase 3), reinstall, retest.

"""Trimiterea emailurilor (doar codul de resetare a parolei)."""
import smtplib
from email.message import EmailMessage

from . import config


def send_reset_code(to: str, name: str, code: str) -> None:
    if not config.SMTP_HOST:
        # mod dezvoltare: fără server de email, afișăm codul în consola backend-ului
        print(f"[DEV] Cod resetare parolă pentru {to}: {code}", flush=True)
        return

    msg = EmailMessage()
    msg["Subject"] = f"Codul tău Topul Aradului: {code}"
    msg["From"] = config.SMTP_FROM
    msg["To"] = to
    msg.set_content(
        f"Salut, {name}!\n\n"
        f"Codul pentru resetarea parolei este: {code}\n\n"
        f"Codul expiră în {config.RESET_CODE_MINUTES} minute. "
        "Dacă nu tu ai cerut resetarea, ignoră acest email.\n\n"
        "👑 Topul Aradului"
    )
    try:
        if config.SMTP_PORT == 465:
            with smtplib.SMTP_SSL(config.SMTP_HOST, config.SMTP_PORT, timeout=15) as s:
                s.login(config.SMTP_USER, config.SMTP_PASSWORD)
                s.send_message(msg)
        else:
            with smtplib.SMTP(config.SMTP_HOST, config.SMTP_PORT, timeout=15) as s:
                s.starttls()
                s.login(config.SMTP_USER, config.SMTP_PASSWORD)
                s.send_message(msg)
    except Exception as e:  # nu dărâmăm cererea dacă emailul eșuează
        print(f"[EMAIL] Nu am putut trimite codul către {to}: {e}", flush=True)

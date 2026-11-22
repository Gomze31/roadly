#!/usr/bin/env python3
"""Inventaire des licences des bibliothèques livrées dans l'APK de Roadly.

Source des faits : le classpath d'exécution de la variante release de `:app` et la licence déclarée dans le POM de chaque
module (ou de son POM parent), résolus par Gradle (`tools/licenses/licences.init.gradle`). Un POM dit ce que l'éditeur déclare ; il ne décrit
pas le code tiers embarqué dans un artefact (bibliothèque native de MapLibre, par exemple).

Usage, depuis la racine du dépôt :

    python tools/licenses/third_party_licenses.py            # réécrit THIRD_PARTY_LICENSES.md
    python tools/licenses/third_party_licenses.py --check    # échoue si le fichier n'est plus à jour ou si une licence est inconnue

Code de sortie non nul : licence hors liste, POM introuvable, ou fichier périmé avec `--check`.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUTPUT = os.path.join(ROOT, "THIRD_PARTY_LICENSES.md")
CONFIGURATION = "releaseRuntimeClasspath"

# Licences admises sans examen : toutes permissives, compatibles avec une distribution sous Apache-2.0.
ALLOWED = ("Apache-2.0", "BSD-2-Clause", "BSD-3-Clause", "MIT")
# Composants non libres connus et acceptés en connaissance de cause (voir le texte du fichier produit).
KNOWN_NON_FREE = "Android Software Development Kit License"
NON_FREE_GROUPS = ("com.google.android.gms",)


def declared_licences():
    """(groupe, nom, version, nom de licence, URL) pour chaque module du classpath d'exécution, lus par Gradle dans les POM."""
    wrapper = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
    init = os.path.join(ROOT, "tools", "licenses", "licences.init.gradle")
    out = subprocess.run(
        [wrapper, "-q", "--no-configuration-cache", "--init-script", init, ":app:roadlyLicences"],
        cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if out.returncode != 0:
        sys.exit("Gradle a échoué : " + out.stderr[-2000:])
    rows = set()
    for line in out.stdout.splitlines():
        if line.startswith("ROADLY_LICENCE|"):
            rows.add(tuple(line.split("|")[1:6]))
    if not rows:
        sys.exit("Gradle n'a listé aucun module : sortie inattendue.")
    return sorted(rows)


def normalise(name, url):
    """Identifiant SPDX quand la déclaration est sans ambiguïté ; sinon le nom déclaré, tel quel."""
    text = (name + " " + url).lower()
    if "android software development kit" in text:
        return KNOWN_NON_FREE
    if "apache" in text:
        return "Apache-2.0"
    if "bsd-3" in text or "bsd 3" in text or "new bsd" in text:
        return "BSD-3-Clause"
    if "bsd-2" in text or "bsd 2" in text or name.strip().lower() == "bsd":
        return "BSD-2-Clause"
    if "mit" in re.findall(r"[a-z]+", text):
        return "MIT"
    return name.strip() or "NON DÉCLARÉE"


HEADER = """# Licences des composants tiers

Ce fichier est produit par `tools/licenses/third_party_licenses.py`. Ne pas le modifier à la main : relancer le script.

Il liste les bibliothèques **livrées dans l'APK** (classpath d'exécution de la variante release de `:app`), avec la licence
déclarée par leur éditeur dans leur POM. Les outils de compilation et de test (compilateurs Room et Hilt, JUnit sous EPL-1.0,
Robolectric sous MIT, Roborazzi, Turbine, MockWebServer) ne sont pas distribués avec l'application et n'y figurent pas.

Le code de Roadly lui-même est distribué sous licence Apache 2.0 (fichier `LICENSE`).

## Composant non libre

Les bibliothèques clientes des services Google Play (`com.google.android.gms`, utilisées par le seul module `core/location`
pour la position) sont publiées sous l'« Android Software Development Kit License »
(<https://developer.android.com/studio/terms>). Ce n'est **pas** une licence libre : leur code source n'est pas publié et
leur redistribution est encadrée par Google. Conséquences :

- un APK de Roadly n'est pas composé à 100 % de logiciel libre ;
- la licence Apache 2.0 de Roadly ne s'applique pas à ces bibliothèques ;
- l'application ne peut pas entrer telle quelle dans un catalogue réservé au logiciel libre (F-Droid).

Leur remplacement par l'API de localisation d'Android est décrit dans `docs/ROADLY-1.0/DECISIONS.md` (section 4).

## Ce que ce fichier ne couvre pas

- Le code tiers embarqué **à l'intérieur** d'un artefact. La bibliothèque native de MapLibre contient ses propres
  dépendances ; leurs mentions sont publiées par le projet MapLibre Native et n'ont pas été vérifiées ici.
- Les polices : Inter et Nunito, SIL Open Font License 1.1 (`core/designsystem/LICENSE-Inter-OFL.txt`,
  `core/designsystem/licenses/Nunito-OFL.txt`).
- Les données et services en ligne (OpenStreetMap sous ODbL, OpenFreeMap, Base Adresse Nationale et IGN sous Licence
  Ouverte 2.0, etc.) : voir `NOTICE` et `docs/SERVICES.md`.

## Obligations par licence

| Licence | Obligation à la redistribution de l'APK |
|---|---|
| Apache-2.0 | Joindre le texte de la licence (`LICENSE`) et le contenu des fichiers `NOTICE` des composants |
| BSD-2-Clause, BSD-3-Clause | Reproduire la mention de droit d'auteur, la liste des conditions et l'exclusion de garantie |
| MIT | Reproduire la mention de droit d'auteur et le texte de permission |
| Android Software Development Kit License | Conditions de Google ; pas de droit de modification ni de redistribution isolée |
"""


def render(rows, totals):
    lines = [HEADER, "## Répartition", "", "| Licence | Artefacts |", "|---|---|"]
    for licence, count in sorted(totals.items(), key=lambda kv: (-kv[1], kv[0])):
        lines.append("| %s | %d |" % (licence, count))
    lines += ["| **Total** | **%d** |" % len(rows), "", "## Artefacts", "",
              "| Artefact | Version | Licence | Déclaration du POM |", "|---|---|---|---|"]
    for group, name, version, licence, declared in rows:
        lines.append("| `%s:%s` | %s | %s | %s |" % (group, name, version, licence, declared or "(aucune)"))
    return "\n".join(lines) + "\n"


def main():
    check = "--check" in sys.argv[1:]
    rows, totals, problems = [], {}, []
    for group, name, version, declared, url in declared_licences():
        if declared == "POM_INTROUVABLE":
            problems.append("POM introuvable : %s:%s:%s" % (group, name, version))
            continue
        found = (declared, url)
        licence = normalise(*found)
        if licence == KNOWN_NON_FREE:
            if not group.startswith(NON_FREE_GROUPS):
                problems.append("Composant non libre inattendu : %s:%s:%s" % (group, name, version))
        elif licence not in ALLOWED:
            problems.append("Licence hors liste (%s) : %s:%s:%s" % (licence, group, name, version))
        rows.append((group, name, version, licence, found[0]))
        totals[licence] = totals.get(licence, 0) + 1
    text = render(rows, totals)
    if check:
        current = open(OUTPUT, encoding="utf-8").read().replace("\r\n", "\n") if os.path.exists(OUTPUT) else ""
        if current != text:
            problems.append("THIRD_PARTY_LICENSES.md n'est plus à jour : relancer tools/licenses/third_party_licenses.py")
    else:
        with open(OUTPUT, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
    for licence, count in sorted(totals.items()):
        print("%4d  %s" % (count, licence))
    print("%4d  artefacts" % len(rows))
    if problems:
        print("\n".join("ERREUR : " + p for p in problems), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

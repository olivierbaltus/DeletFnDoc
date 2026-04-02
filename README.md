# filenet-wsi-delete

Petit utilitaire Java/Maven qui supprime des documents FileNet à partir d'une requête CE SQL, en utilisant le transport **WSI / CEWS** (`/wsi/FNCEWS40MTOM`).

## Ce que fait le programme

- connexion à FileNet via **WSI**
- authentification avec `UserContext.createSubject(...)`
- exécution d'une requête **CE SQL**
- affichage des documents trouvés
- suppression :
  - soit **de la version trouvée uniquement**
  - soit **de toute la VersionSeries** avec `--delete-all-versions`
- mode **dry-run** recommandé avant toute suppression réelle

## Références IBM utilisées

IBM indique que le transport CEWS/WSI côté Java est déterminé par l'URI, par exemple `http://remotehost1:9080/wsi/FNCEWS40MTOM`, et qu'une application Java peut établir le contexte JAAS avec `UserContext.createSubject(...)`. citeturn516009view0

IBM indique aussi qu'une suppression de document se fait en appelant `delete()` puis `save(RefreshMode.NO_REFRESH)`. citeturn516009view1turn825425view2

Enfin, IBM précise qu'on peut supprimer soit une seule version, soit toute la `VersionSeries`. citeturn825425view4

## Pré-requis

- Java 11+
- Maven 3.8+
- accès réseau au CE via WSI
- droits FileNet suffisants pour supprimer les documents ciblés
- **jace.jar** de l'API Java FileNet installé dans le repository Maven local

## Installation de `jace.jar` dans le repository Maven local

Comme les librairies FileNet ne sont généralement pas disponibles sur Maven Central, il faut installer `jace.jar` localement.

### Linux / macOS

```bash
mvn install:install-file \
  -Dfile=/chemin/vers/jace.jar \
  -DgroupId=com.ibm.filenet \
  -DartifactId=jace \
  -Dversion=5.5.12.0 \
  -Dpackaging=jar
```

### Windows

```bat
mvn install:install-file ^
  -Dfile=C:\chemin\vers\jace.jar ^
  -DgroupId=com.ibm.filenet ^
  -DartifactId=jace ^
  -Dversion=5.5.12.0 ^
  -Dpackaging=jar
```

> Adapte la version si ton `jace.jar` correspond à une autre release FileNet.

## Build

```bash
mvn clean package
```

## Exemples d'utilisation

### 1) Dry-run

```bash
mvn exec:java -Dexec.args=" \
  --uri https://filenet.example.com/wsi/FNCEWS40MTOM \
  --username myuser \
  --password mypassword \
  --object-store OS1 \
  --query \"SELECT * FROM Document WHERE DocumentTitle LIKE 'TEST% '\" \
  --dry-run"
```

### 2) Suppression des versions trouvées uniquement

```bash
mvn exec:java -Dexec.args=" \
  --uri https://filenet.example.com/wsi/FNCEWS40MTOM \
  --username myuser \
  --password mypassword \
  --object-store OS1 \
  --query \"SELECT * FROM Document WHERE Creator = 'p8admin'\""
```

### 3) Suppression de toutes les versions de chaque document trouvé

```bash
mvn exec:java -Dexec.args=" \
  --uri https://filenet.example.com/wsi/FNCEWS40MTOM \
  --username myuser \
  --password mypassword \
  --object-store OS1 \
  --query \"SELECT * FROM Document WHERE DateCreated < DATE('2024-01-01')\" \
  --delete-all-versions"
```

### 4) Continuer malgré les erreurs

```bash
mvn exec:java -Dexec.args=" \
  --uri https://filenet.example.com/wsi/FNCEWS40MTOM \
  --username myuser \
  --password mypassword \
  --object-store OS1 \
  --query \"SELECT * FROM Document WHERE DocumentTitle LIKE 'TMP-%'\" \
  --continue-on-error"
```

## Conseils importants

- Commence toujours par `--dry-run`.
- Évite les requêtes trop larges sans `WHERE`.
- Teste d'abord avec `--max 10`.
- Si tu utilises `--delete-all-versions`, une seule correspondance peut supprimer **toute** la série de versions.
- IBM rappelle que l'opération de suppression échoue entièrement si les droits ne couvrent pas toutes les versions / dépendances nécessaires. citeturn516009view1turn825425view4

## Exemples de requêtes CE SQL

```sql
SELECT * FROM Document WHERE Id = OBJECT('{XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX}')
```

```sql
SELECT * FROM Document WHERE DocumentTitle LIKE 'TEST%'
```

```sql
SELECT * FROM Document WHERE Creator = 'p8admin' AND DateCreated < DATE('2024-01-01')
```

## Limites / adaptations possibles

Cette version est volontairement simple. Selon ton besoin, tu peux facilement l'étendre pour :

- ajouter un fichier `.properties` au lieu de passer les paramètres en ligne de commande
- journaliser dans Log4j / SLF4J
- faire un export CSV des documents trouvés avant suppression
- filtrer sur une classe documentaire spécifique
- supprimer à partir d'une liste d'IDs plutôt qu'une requête CE SQL
- gérer OAuth / Bearer token si ton environnement WSI est fédéré autrement

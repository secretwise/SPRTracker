# SPRTracker
отслеживайте снос тотемов,  бумбум кристалов, и убийства игроков, подойдет для ваших мега ультра ванилла серверов

# paper 1.21.11

# build 
gradlew build

# permissions

- `tracker.use` - access to `/tracker`
- `tracker.alerts` - get notifications
- `tracker.history` - `/tracker history`
- `tracker.admin` - `/tracker reload`, `/tracker test`, `/tracker webhook`

# commands

/tracker alerts [totem|crystal|kill]
/tracker sound
/tracker actionbar
/tracker history [n]
/tracker test [totem|crystal|kill]
/tracker webhook [on|off|test]
/tracker reload


webhook off/on

# config

enter ur webhook if u wanna log to discord

# ArlightBingo

Plugin de minijuego Bingo para servidores hibridos (Bukkit/Paper + Forge/NeoForge, tipo Arclight), Minecraft 1.21.1.

## Como compilar

Necesitas Java 21 y Maven instalados.

**Importante**: este proyecto tiene una dependencia (opcional, en `pom.xml`) a `ArlightCore`
(el nucleo compartido de minijuegos). Si tenes el proyecto `ArlightCore` aparte, compilalo e
instalalo PRIMERO con `mvn install` ahi, o si no queres usar esa integracion, borra el bloque
`<dependency>` de ArlightCore en `pom.xml` y la clase
`src/main/java/com/arlight/bingo/integration/CoreIntegration.java`.

```
cd BingoPlugin
mvn clean package
```

El jar final queda en `target/ArlightBingo-1.0.0.jar`. Ese es el archivo que copias a la carpeta `/plugins` del servidor.

## Flujo de una partida

1. Los jugadores se unen con `/bingo join` o con el cartel `[Bingo]` (ver mas abajo). Mientras se
   espera, el scoreboard muestra `Jugadores: X/10`.
2. En cuanto hay `min-players-to-start` (2 por defecto) o mas jugadores anotados, arranca solo una
   cuenta regresiva de `auto-start-countdown-seconds` (50s por defecto), visible en el scoreboard y
   anunciada en el chat. Si baja gente y quedan menos del minimo, la cuenta se cancela.
3. Al llegar esa cuenta a 0 (o si un admin fuerza `/bingo start` antes), se regenera el mundo de
   arena (mini isla segura, ver mas abajo) y se teletransporta a todos ahi.
4. RECIEN AHI, ya parados en la arena, arranca la cuenta final "5, 4, 3, 2, 1... COMIENZA!" y se
   reparten los cartones -- empieza la partida de verdad.
5. Durante la partida, el scoreboard muestra dos lineas por jugador/equipo: sus puntos, y el
   ultimo objetivo que completo (se actualiza en el mismo lugar, no se acumula una lista larga).
   Si hay `time-limit-minutes` configurado, tambien aparece una boss bar arriba con el tiempo restante.
6. Cuando alguien gana (o se acaba el tiempo en modo por puntos y alguien sumo puntos), se festeja:
   titulo en pantalla "¡GANASTE EL BINGO!" + fuegos artificiales para el ganador, y un titulo
   distinto para el resto avisando quien gano. Recien despues de `celebration-seconds` (8s por
   defecto) se devuelve a todos al lobby.

## Comandos

- `/bingo start` (admin) - fuerza el inicio ya mismo (salta la cuenta de 50s, hace la cuenta final 5-4-3-2-1)
- `/bingo stop` (admin) - detiene la partida y devuelve jugadores al lobby (si aplica)
- `/bingo reload` (admin) - recarga config.yml
- `/bingo trigger <jugador> <idObjetivo>` (admin) - completa manualmente un objetivo CUSTOM_TRIGGER
- `/bingo world lobby <mundo>` (admin) - setea el mundo lobby
- `/bingo world game <mundo>` (admin) - setea el (unico) mundo de partida
- `/bingo world info` (admin) - muestra los mundos configurados
- `/bingo world resetarena [mundo]` (admin) - confirma que el plugin tome control de un mundo
  YA EXISTENTE en el disco (que no creo el mismo) y lo regenera ahora mismo. Necesario una vez
  si apuntaste `game-world` a un mundo que ya tenias, para que el plugin lo pueda regenerar despues.
- `/bingo vanillaonly <true|false>` (admin) - activa/desactiva incluir contenido de mods en la generacion automatica
- `/bingo blacklist item [namespace:id]` (admin) - banea un item de la generacion automatica (si no das el id, usa el item en tu mano)
- `/bingo blacklist mod [modid]` (admin) - banea un mod entero (si no das el modid, usa el mod del item en tu mano)
- `/bingo blacklist removeitem <namespace:id>` / `removemod <modid>` / `list` (admin)
- `/bingo join [equipo]` - se une a la partida (el nombre de equipo solo aplica si `team-mode: true`)
- `/bingo leave` - abandona la partida
- `/bingo card` - abre una GUI (inventario) con tu carton: verde = completado, item real = pendiente
- `/bingo carditem` - te da (o te vuelve a dar si lo perdiste) el item fisico "Carton de Bingo"
- `/bingo lobby` - te teletransporta al mundo lobby
- `/bingo arena` - te teletransporta al mundo de la partida en curso (si estas anotado)

## Item fisico del carton

Al unirte con `/bingo join` (o con el cartel) recibis automaticamente un item "Carton de Bingo"
(un mapa). Click derecho con el en la mano abre la misma GUI que `/bingo card`. Si lo pierdes o
lo tiras, `/bingo carditem` te da uno nuevo.

Ademas, una vez que tenes un carton asignado (arranco la partida), el mapa en si mismo muestra
una grilla de colores con tu progreso (verde = casilla completada, gris = pendiente) y se
actualiza solo mientras lo sostenes -- una vista rapida sin tener que abrir la GUI. No llega a
mostrar el icono real de cada item dentro del mapa (Minecraft no da una forma simple de dibujar
iconos de items ahi sin recursos graficos externos), pero da un pantallazo del progreso general.

## Deteccion de objetivos: como funciona por dentro

- **Romper/colocar bloques, matar entidades, logros**: se detectan al instante con eventos de Bukkit.
- **Conseguir items / craftear items**: en vez de depender de eventos (recoger del suelo, craftear en
  una mesa vanilla -- poco confiables, no cubren hornos, cofres, trueques, ni sistemas de
  auto-recogida de mods), el plugin escanea el inventario completo de cada jugador **cada segundo**
  mientras la partida esta corriendo, y usa el maximo historico visto de cada item objetivo. Asi,
  aunque despues gastes o craftees el item en otra cosa, ya cuenta como conseguido.

## Objetivos generados automaticamente

Por defecto (`goals.auto-generate: true`) el plugin genera solo un pool de objetivos aleatorios
(items para conseguir, bloques para romper, mobs para matar) a partir de los registros del propio
juego, sin que tengas que escribir nada a mano. Se descartan bloques/items no obtenibles en survival
normal: `bedrock`, `barrier`, `command_block`, `structure_block`, `jigsaw`, `spawner`, etc.

**Por defecto SOLO se usa contenido vanilla** (`goals.vanilla-only: true`) -- nada de mods. Esto
evita objetivos raros/imposibles de items de mods sin tener que andar armando una blacklist. Para
incluir tambien contenido de mods: `/bingo vanillaonly false` (o editar `goals.vanilla-only` en el
config). Si activas mods y aparece algo problematico, usa la blacklist (mas abajo) para excluirlo.

Esto es un filtro *best-effort* (no hay una lista 100% perfecta de "todo lo imposible de conseguir"),
asi que si ves algun objetivo raro colarse, puedes reportarlo o pasar a `auto-generate: false` y usar
la lista `goals.manual` (queda en el config.yml como referencia/ejemplo) para tener control total.

Tipos soportados: `ITEM_COLLECT`, `CRAFT_ITEM`, `BLOCK_BREAK`, `BLOCK_PLACE`, `ENTITY_KILL`,
`ADVANCEMENT`, `CUSTOM_TRIGGER` (este ultimo solo aplica en modo manual, para logica de mods que
no dispara eventos Bukkit; se completa con `/bingo trigger`).

Cada objetivo automatico pide **1 unidad** (conseguir/romper/matar 1), no cantidades variables.

### Blacklist de items y mods

Si algun item o mob generado no te gusta (muy raro, requiere mucho grindeo, rompe el balance, etc.),
podes banearlo sin tocar el config.yml a mano:

```
/bingo blacklist item          # banea el item que tenes en la mano
/bingo blacklist item minecraft:netherite_ingot   # o el id explicito
/bingo blacklist mod            # banea TODO el mod del item que tenes en la mano
/bingo blacklist mod modid_ejemplo
/bingo blacklist list           # ver que esta baneado
```

El cambio se guarda en `config.yml` (secciones `goals.blacklist-items` y `goals.blacklist-mods`)
y el pool de objetivos se regenera al instante, sin necesidad de `/bingo reload`.

## Inventario limpio en cada partida nueva

Al arrancar una partida (sea por auto-inicio o `/bingo start`), se limpia el inventario, armadura
y mano secundaria de todos los jugadores anotados antes de repartir cartones. Esto evita que items
de una partida anterior den progreso gratis en la nueva (el escaneo de inventario detectaria items
viejos como "ya conseguidos") y evita que el inventario se acumule partida tras partida. Se les
vuelve a dar el item del carton despues de limpiar.

## GUI en vivo

La GUI del carton (`/bingo card` o el item fisico) se actualiza sola mientras la tenes abierta:
no hace falta cerrarla y volver a abrirla para ver el progreso nuevo.

## Borde de mundo adaptativo (rendimiento + acceso a estructuras)

Cada vez que se prepara una arena, el plugin:

1. Busca (con la API de estructuras de Bukkit, sin necesidad de que nadie explore) donde cayo
   para esa seed en particular: el **stronghold** en el mundo principal, el **bastion/fortaleza**
   en el mundo Nether extra (si hay uno configurado), y la **ciudad del End** en el mundo End
   extra (si hay uno configurado).
2. Ajusta el `WorldBorder` de cada mundo para que esas estructuras queden DENTRO del area
   jugable (nunca las deja afuera, salvo que se pase del limite de seguridad `max-size`).
3. Pregenera (de forma asincrona, sin trabar el servidor) una zona bastante mas chica alrededor
   del spawn (`pregenerate-radius`) -- esto es lo que evita el lag de "generar terreno en vivo"
   mientras juegan, que es la causa mas comun de tirones durante una partida.

Importante: el TAMANO DEL BORDE y el TAMANO DE LO PREGENERADO son cosas distintas a proposito.
Un stronghold puede estar, por diseno del propio Minecraft, a varios miles de bloques del spawn
-- pregenerar TODO ese camino tardaria demasiado antes de cada partida. Por eso el borde se
agranda lo necesario para que sea *alcanzable*, pero solo se pregenera la zona chica cercana al
spawn; si un equipo camina hasta el stronghold lejano, esa zona puntual se genera normalmente en
el momento (un costo raro y puntual, no el lag constante que queriamos evitar).

Se configura en la seccion `world-border` de `config.yml` (ver los comentarios ahi para cada
opcion: `min-size`, `padding`, `max-size`, `search-radius`, `pregenerate-radius`, y los
`locate-*` para activar/desactivar la busqueda de cada tipo de estructura).

**Nota de compatibilidad**: esto usa `World#locateNearestStructure` y el registro de
estructuras de Bukkit (`org.bukkit.generator.structure`), que es API "clasica" de Bukkit (no
una extension exclusiva de Paper), asi que deberia funcionar en Arclight -- pero como no lo pude
probar en tu servidor real, si algo falla avisame el error de consola y lo ajustamos. Tambien
puede haber una pausa breve (hasta un par de segundos) durante "Preparando la arena..." mientras
se buscan las estructuras -- es una operacion sincrona por diseno de Minecraft, pero solo pasa
una vez por partida, en un momento donde nadie esta jugando activamente todavia.

## Mundo de arena (nativo, sin plugins externos)

Ya NO depende de Multiverse-Core ni de ningun otro plugin: el mundo de arena se crea y se
regenera (se borra del disco y se vuelve a crear con una seed aleatoria) con la propia API
de Bukkit. Si el mundo no existe todavia, el plugin lo crea solo la primera vez.

Pasos para configurarlo (por defecto ya viene activado):

1. En `config.yml`, seccion `arena-world`: `enabled: true` (default), `lobby-world` (mundo donde
   esperan los jugadores -- por defecto `"world"`, el mundo principal, que ya existe de entrada) y
   `game-world` (el mundo de la partida -- por defecto `"bingo_arena"`, se crea solo).
   Tambien podes usar `/bingo world lobby <mundo>` / `/bingo world game <mundo>` en vez de editar
   el archivo a mano.
2. Cada vez que arranca una partida, el plugin borra y vuelve a crear ese mundo (seed nueva) y
   teletransporta a todos los jugadores anotados. Al terminar la partida, todos vuelven al `lobby-world`.
3. Cada vez que se regenera, se construye una pequena plataforma seria y segura (una "mini isla",
   9x9, en coordenadas fijas) y se fija ahi el punto de spawn del mundo -- para que nadie aparezca
   nunca enterrado ni en un lugar peligroso al entrar a la arena.

**Proteccion anti-borrado**: si apuntas `game-world` a un mundo que YA EXISTIA en el disco de antes
(por ejemplo un mapa a medida que tenias para otra cosa) y el plugin nunca lo creo, el plugin **no lo
borra automaticamente** -- lo carga tal cual y avisa en la consola. Si de verdad queres que el
plugin tome control de ese mundo y lo regenere en cada partida, confirmalo una vez con
`/bingo world resetarena`.

**Importante**: si cambias `lobby-world` a un mundo que no existe, el teletransporte al lobby
fallara silenciosamente (revisa la consola). Asegurate de que ese mundo si exista.

### Cartel para unirse/entrar

Cualquier jugador con permiso `arlightbingo.admin` puede crear un cartel con la primera
linea `[Bingo]`. Al hacer click derecho sobre el:
- Si la partida esta esperando jugadores o en cuenta regresiva: el jugador se une (como `/bingo join`)
  y es teletransportado al lobby.
- Si la partida ya esta corriendo y el jugador esta anotado: lo teletransporta directo a la arena.

## Persistencia

El estado de la partida (equipos, cartones, progreso, mundo de arena) se guarda automaticamente en
`plugins/ArlightBingo/gamedata.yml` despues de cada cambio importante y tambien al apagar el plugin.
Al arrancar de nuevo, si habia una partida en curso, se restaura tal cual estaba (incluyendo el
tiempo restante si hay limite configurado). Nota: la cuenta regresiva de sala de espera y la cuenta
final NO se restauran tras un reinicio (solo aplica a partidas ya en estado RUNNING).

## Modo de victoria

- **POINTS** (por defecto): 1 punto por casilla completada. Se sigue jugando hasta que se acaba
  el `time-limit-minutes` configurado, y ahi gana quien tenga mas puntos (empate si hay igualdad).
  Completar el carton entero sigue siendo una victoria instantanea (bonus). **Importante**: este
  modo necesita `time-limit-minutes` mayor a 0 para poder terminar solo (si lo dejas en 0, la
  partida nunca termina sola salvo que alguien complete el carton entero).
- **LINE**: gana el primero en completar una fila/columna/diagonal.
- **FULL_CARD** / **BLACKOUT**: gana el primero en completar el carton entero.

## Configuracion

Todo se ajusta en `config.yml`: tamano del carton, modo equipos on/off, condicion de victoria,
umbral de jugadores y duracion de las cuentas regresivas, generacion de objetivos, y el
manejo del mundo de arena.

## Ideas para seguir extendiendo

- Integracion con PlaceholderAPI para reusar estos datos en otros scoreboards/menus.
- Barra de progreso (boss bar) ademas del scoreboard durante la cuenta regresiva.
- Objetivos de "visitar bioma/estructura" usando PlayerMoveEvent con throttling.

## Autocompletado de comandos

`/bingo <tab>` sugiere todos los subcomandos, y varios de ellos autocompletan tambien sus
argumentos (nombres de jugadores conectados, nombres de mundos cargados, ids de objetivos del
carton de un jugador para `/bingo trigger`, items/mods ya baneados para los `removeitem`/`removemod`
de la blacklist, etc).

## Sala de espera: items de informacion y salir

Al unirte (por `/bingo join` o por el cartel) tu inventario se limpia por completo y recibis dos
items temporales mientras esperas que arranque la partida:
- **¿De que va el Bingo?** (libro): explica el objetivo, el modo (individual/equipos), como se
  gana y la duracion, tomando los valores reales de tu `config.yml`.
- **Salir de la sala** (tinte rojo): te saca de la partida en espera y te devuelve al mundo
  principal del servidor con el inventario limpio.

Estos dos items se reemplazan por el item real del carton (ver mas abajo) recien cuando arranca
la partida de verdad.

## Objetivos en espanol

Los objetivos generados automaticamente (items, bloques, mobs) se muestran en espanol, usando un
diccionario interno con las traducciones de lo mas comun del juego vanilla. Si algun nombre no
esta en el diccionario, se muestra en ingles como respaldo (nunca se rompe, solo se ve en ingles
ese caso puntual) -- se puede ampliar editando `SpanishNames.java` y recompilando.

Tambien se saco a los huevos de generar criaturas (`_SPAWN_EGG`) del pool automatico, ya que no
tiene sentido pedir "consigue el huevo de X" como objetivo de supervivencia.

## Nether/End propios de la arena (mundos vinculados)

Si armaste un Nether y/o un End propios para la arena del bingo (por ejemplo con
Multiverse-NetherPortals, conectando `bingo_arena` <-> `bingo_arena_nether` / `bingo_arena_the_end`),
agregalos a `arena-world.extra-worlds` en el config (o con `/bingo world addextra <mundo>`) para
que el plugin los regenere junto con el mundo principal en cada partida:

```
/bingo world addextra bingo_arena_nether
/bingo world addextra bingo_arena_the_end
```

Como esos mundos probablemente ya existian en el disco (los creaste vos a mano antes de instalar
el plugin), aplica la misma proteccion anti-borrado que el mundo principal: la primera vez hay que
confirmar que el plugin puede tomar control de cada uno:

```
/bingo world resetarena bingo_arena_nether
/bingo world resetarena bingo_arena_the_end
```

Despues de esa confirmacion, se regeneran solos en cada partida igual que la arena principal.

## Muerte, respawn y estado al entrar a la arena

- Si un jugador muere estando en la arena (o en uno de sus mundos extra), respawnea en el spawn
  del `lobby-world` en vez del mundo por defecto del servidor.
- Cada vez que un jugador entra a la arena (o a uno de sus mundos extra) -- ya sea al arrancar la
  partida, por respawn, o usando `/bingo arena` -- se lo pone en modo supervivencia, se le quitan
  todos los efectos de pocion activos, y se le resetea la vida (maxima y actual) a 20 corazones
  llenos y el hambre al maximo, para que todos arranquen en igualdad de condiciones.

## Integracion con ArlightCore

Si tenes instalado el plugin `ArlightCore` (el nucleo compartido de minijuegos), el Bingo se
registra solo en su item selector de minijuegos, y da la XP configurada ahi (+5 por defecto) a
quien gane. Esto es 100% opcional: si ArlightCore no esta instalado, el Bingo funciona
exactamente igual sin esto.

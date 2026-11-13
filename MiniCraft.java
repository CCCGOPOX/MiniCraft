import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;



































/**
 * MiniCraft 0.2 - 3D-игра в стиле Minecraft на чистой Java (только стандартный JDK).
 *
 * Что внутри:
 *  - заставка и главное меню (поверх живого облёта мира)
 *  - бесконечный мир из чанков 16x64x16; чанки генерируются в фоновом потоке
 *    только там, куда вы смотрите; то, что далеко позади, выгружается
 *  - режим "Творчество" и режим "Выживание" (здоровье, добыча, инвентарь,
 *    урон от падения, смена дня и ночи, ночью приходят зомби)
 *  - программный рендер: лучи через воксели (DDA), видеокарта не нужна
 *
 * Запуск:  javac -encoding UTF-8 MiniCraft.java
 *          java MiniCraft
 */
public class MiniCraft extends JPanel implements Runnable {

    // ====================================================================
    //  Константы
    // ====================================================================
    static final int CS = 16, WY = 64, RING = 256;           // размер чанка, высота мира, кольцо чанков (кэш загруженных)
    int RW = 480, RH = 270;                     // внутреннее разрешение рендера
    static final double REACH = 6;                           // дальность руки
    static final int MAXHP = 20;                             // 20 = 10 сердец
    static final double DAY_LEN = 240;                       // длина суток в секундах (для фона меню)
    static final double DAY_SEC = 600, NIGHT_SEC = 300;      // день 10 минут, ночь 5 минут

    /** Скорость хода времени: пока солнце над горизонтом - медленнее, ночью - быстрее. */
    static double todRate(double t) { return (t >= 0.25 && t < 0.75) ? 0.5 / DAY_SEC : 0.5 / NIGHT_SEC; }
    static final double SPLASH_LEN = 5.0;
    static double BIO_D = 0.62, BIO_S = 0.38;
    static final int SAND_H = 24, SNOW_H = 44, SEA = 22, WATER = 10;
    static final int B_COAL = 11, B_IRON = 12, B_GOLD = 16, B_DIAM = 17, B_GLOW = 19;
    static final int I_COAL = 32, I_IRON = 33, I_INGOT = 34, I_STICK = 35, I_MEAT = 36, I_GOLD = 37, I_DIAM = 38,
            I_SEEDS = 39, I_WHEAT = 40, I_BREAD = 41, I_COOKED = 42, I_ARM_I = 43, I_ARM_D = 44, I_FLINT = 45, I_GAPPLE = 46;
    static final int B_CHEST = 23, B_FURN = 24, B_BED = 25, B_CROP = 26, B_RIPE = 27, B_CACTUS = 28, B_SANDST = 29, B_GLASS = 30,
            B_DOOR_L = 48, B_DOOR_U = 49, B_DOOR_LO = 50, B_DOOR_UO = 51, B_TRAP = 52, B_TRAP_O = 53,
            B_SHELF = 54, B_OBSI = 55, B_WOOL_R = 56, B_WOOL_B = 57, B_LANT = 58, B_NRACK = 59, B_MAGMA = 60, B_PORTAL = 61;
    static final int NX = 1000000, NT = 500000;                           // всё, что правее этой координаты X, - Нижний мир   // двери и люки (id 48+)
    static final int B_LAMP = 84, B_TNT = 85;
    static boolean emits(int id) { return id == B_GLOW || id == B_LANT || id == B_LAMP; }
    static final int NITEM = 96;                             // блоки 1..29, предметы 32..42

    static final int[] BASE = new int[NITEM];
    static final String[] NAMES = new String[NITEM];
    static final double[] HARD = new double[NITEM];          // секунд при уровне инструмента 0 (-1 = не ломается)
    static final int[] TOOLT = new int[NITEM];               // 0 рука, 1 кирка, 2 топор
    static final int[] NEEDT = new int[NITEM];               // минимальный уровень, чтобы блок что-то дал
    static final int[] DROP = new int[NITEM];
    static final double[] SHORT_H = new double[NITEM];      // высота низких блоков (кровать, всходы), которые не занимают клетку целиком
    static final double[] TIERM = {1, 2.6, 4.5, 7, 10};     // ускорение по уровням инструмента
    static final int[] SWORD_DMG = {2, 4, 5, 7, 9};
    static final String[] TIER_NAMES = {"нет", "деревянная", "каменная", "железная", "алмазная"};

    static void blk(int id, String name, int color, double hard, int tool, int need, int drop) {
        NAMES[id] = name; BASE[id] = color; HARD[id] = hard; TOOLT[id] = tool; NEEDT[id] = need; DROP[id] = drop;
    }

    static {
        Arrays.fill(NAMES, "");
        blk(1, "Трава", 0x5FAA3C, 0.6, 0, 0, 2);
        blk(2, "Земля", 0x8B5E3C, 0.6, 0, 0, 2);
        blk(3, "Камень", 0x808080, 5, 1, 1, 15);
        blk(4, "Доски", 0xB88A4A, 2.5, 2, 0, 4);
        blk(5, "Листва", 0x3E8C2E, 0.3, 0, 0, 5);
        blk(6, "Бревно", 0x6B4A2B, 3, 2, 0, 6);
        blk(7, "Бедрок", 0x3A3A3A, -1, 0, 0, 0);
        blk(8, "Песок", 0xDDD3A0, 0.5, 0, 0, 8);
        blk(9, "Снег", 0xF4F8FF, 0.4, 0, 0, 9);
        blk(10, "Вода", 0x2C64B0, -1, 0, 0, 0);
        blk(11, "Угольная руда", 0x808080, 7, 1, 1, I_COAL);
        blk(12, "Железная руда", 0x808080, 9, 1, 2, I_IRON);
        blk(13, "Кирпич", 0xA84A3A, 5, 1, 1, 13);
        blk(14, "Гравий", 0x8E8A86, 0.7, 0, 0, 14);
        blk(15, "Булыжник", 0x6E6E6E, 5, 1, 1, 15);
        blk(16, "Золотая руда", 0x808080, 10, 1, 3, I_GOLD);
        blk(17, "Алмазная руда", 0x808080, 12, 1, 3, I_DIAM);
        blk(18, "Шерсть", 0xEFEFEF, 0.6, 0, 0, 18);
        blk(19, "Светокамень", 0xFFD060, 1.5, 0, 0, 19);
        blk(20, "Глина", 0x9AA3B8, 0.8, 0, 0, 20);
        blk(21, "Тёмные доски", 0x5A3F26, 2.5, 2, 0, 21);
        blk(22, "Каменные кирпичи", 0x8C8C8C, 5, 1, 1, 22);
        blk(B_CHEST, "Сундук", 0x9A6A30, 2.5, 2, 0, B_CHEST);
        blk(B_FURN, "Печка", 0x7C7C7C, 4, 1, 1, B_FURN);
        blk(B_BED, "Кровать", 0xC83C3C, 0.6, 0, 0, B_BED);
        blk(B_CROP, "Всходы пшеницы", 0x4FA83A, 0.05, 0, 0, I_SEEDS);
        blk(B_RIPE, "Спелая пшеница", 0xD8B848, 0.05, 0, 0, I_WHEAT);
        blk(B_CACTUS, "Кактус", 0x2E7D32, 0.6, 0, 0, B_CACTUS);
        blk(B_SANDST, "Песчаник", 0xD8C38A, 1.5, 1, 0, B_SANDST);
        blk(B_GLASS, "Стекло", 0xCFEFF8, 0.4, 0, 0, B_GLASS);
        blk(B_DOOR_L, "Дверь", 0x9A6B34, 1.0, 2, 0, B_DOOR_L);
        blk(B_DOOR_U, "Дверь", 0x9A6B34, 1.0, 2, 0, B_DOOR_L);
        blk(B_DOOR_LO, "Дверь (открыта)", 0x9A6B34, 1.0, 2, 0, B_DOOR_L);
        blk(B_DOOR_UO, "Дверь (открыта)", 0x9A6B34, 1.0, 2, 0, B_DOOR_L);
        blk(B_TRAP, "Люк", 0x8A5C2C, 1.0, 2, 0, B_TRAP);
        blk(B_TRAP_O, "Люк (открыт)", 0x8A5C2C, 1.0, 2, 0, B_TRAP);
        blk(B_SHELF, "Книжная полка", 0x8A6A3A, 1.5, 2, 0, B_SHELF);
        blk(B_OBSI, "Обсидиан", 0x241638, 22, 1, 4, B_OBSI);
        blk(B_WOOL_R, "Красная шерсть", 0xC03030, 0.6, 0, 0, B_WOOL_R);
        blk(B_WOOL_B, "Синяя шерсть", 0x3050C0, 0.6, 0, 0, B_WOOL_B);
        blk(B_LANT, "Фонарь", 0xFFC850, 0.5, 0, 0, B_LANT);
        blk(B_NRACK, "Адский камень", 0x7A2E2E, 2.5, 1, 0, B_NRACK);
        blk(B_MAGMA, "Магма", 0xE05A10, 4, 1, 1, 0);
        blk(B_PORTAL, "Портал", 0x8A3CE0, 0.2, 0, 0, 0);
        blk(64, "Белый бетон", 0xE8E8E8, 1.8, 1, 0, 64);
        blk(65, "Чёрный бетон", 0x24242A, 1.8, 1, 0, 65);
        blk(66, "Красный бетон", 0xB02E26, 1.8, 1, 0, 66);
        blk(67, "Синий бетон", 0x2E3AA0, 1.8, 1, 0, 67);
        blk(68, "Зелёный бетон", 0x4A6A20, 1.8, 1, 0, 68);
        blk(69, "Жёлтый бетон", 0xE8C020, 1.8, 1, 0, 69);
        blk(70, "Оранжевая терракота", 0xA85A32, 1.5, 1, 0, 70);
        blk(71, "Белая терракота", 0xD2B8A8, 1.5, 1, 0, 71);
        blk(72, "Полированный камень", 0x9C9C9C, 4, 1, 1, 72);
        blk(73, "Гладкий кварц", 0xECE6DD, 3, 1, 1, 73);
        blk(74, "Призмарин", 0x4A9A8A, 4, 1, 1, 74);
        blk(75, "Замшелый булыжник", 0x62806A, 4, 1, 1, 75);
        blk(76, "Колонна", 0xD8D0C0, 3, 1, 1, 76);
        blk(77, "Золотой блок", 0xF0D040, 6, 1, 2, 77);
        blk(78, "Железный блок", 0xDADADA, 6, 1, 2, 78);
        blk(79, "Алмазный блок", 0x4AE0D0, 8, 1, 3, 79);
        blk(80, "Угольный блок", 0x1C1C1C, 5, 1, 1, 80);
        blk(81, "Паркет", 0xC89A5A, 2, 2, 0, 81);
        blk(82, "Светлые доски", 0xE0CC90, 2, 2, 0, 82);
        blk(83, "Шахматная плитка", 0xF0F0F0, 3, 1, 1, 83);
        blk(84, "Лампа", 0xFFE0A0, 0.8, 0, 0, 84);
        blk(B_TNT, "TNT", 0xC83030, 0.3, 0, 0, B_TNT);
        SHORT_H[B_BED] = 0.5625;
        SHORT_H[B_CROP] = 0.5;
        SHORT_H[B_RIPE] = 0.9;
        blk(I_COAL, "Уголь", 0x2B2B2B, 0, 0, 0, 0);
        blk(I_IRON, "Сырое железо", 0xC8A07A, 0, 0, 0, 0);
        blk(I_INGOT, "Железный слиток", 0xE0E0E8, 0, 0, 0, 0);
        blk(I_STICK, "Палки", 0x9A7B4A, 0, 0, 0, 0);
        blk(I_MEAT, "Мясо", 0xD88888, 0, 0, 0, 0);
        blk(I_GOLD, "Золото", 0xF0C030, 0, 0, 0, 0);
        blk(I_DIAM, "Алмаз", 0x40E0E0, 0, 0, 0, 0);
        blk(I_SEEDS, "Семена", 0x6B8E23, 0, 0, 0, 0);
        blk(I_WHEAT, "Пшеница", 0xE0C050, 0, 0, 0, 0);
        blk(I_BREAD, "Хлеб", 0xC8864A, 0, 0, 0, 0);
        blk(I_COOKED, "Жареное мясо", 0x9B5A3C, 0, 0, 0, 0);
        blk(I_GAPPLE, "Золотой хлеб", 0xF0D040, 0, 0, 0, 0);
        blk(I_FLINT, "Огниво", 0x55555F, 0, 0, 0, 0);
        blk(I_ARM_I, "Железная броня", 0xD0D0D8, 0, 0, 0, 0);
        blk(I_ARM_D, "Алмазная броня", 0x40E0E0, 0, 0, 0, 0);
    }

    /** Какие блоки лежат на панели быстрого доступа (survival: только добываемые, creative: все). */
    static final int[] PLACE_SURV = {2, 15, 4, 5, 6, 8, 9, 13, 14, 18, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 48, 52, 54, 55, 56, 57, 58, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85};
    static final int[] PLACE_CREA = {1, 2, 3, 4, 5, 6, 8, 9, 13, 14, 15, 18, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 48, 52, 54, 55, 56, 57, 58, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 10, 11, 12, 16, 17};

    /** Можно ли класть предмет на панель автоматически: блоки и мясо. */
    static boolean hotable(int id) {
        if (id == I_MEAT || id == I_COOKED || id == I_BREAD || id == I_SEEDS) return true;
        for (int p : PLACE_CREA) if (p == id) return true;
        return false;
    }

    void autoHot(int id) {
        if (!hotable(id)) return;
        for (int h : hot) if (h == id) return;
        for (int i = 0; i < hot.length; i++) if (hot[i] == 0) { hot[i] = id; return; }
    }

    /** Добавить предметы в инвентарь (новый вид сам встаёт в свободную ячейку панели). */
    static final String[][] ACH = {
            {"wood", "Первое дерево", "Добудьте древесину"}, {"coal", "Чёрное золото", "Добудьте уголь"},
            {"iron", "Железный век", "Выплавите железный слиток"}, {"gold", "Блеск", "Получите золото"},
            {"diamond", "Алмазы!", "Добудьте алмаз"}, {"bread", "Пекарь", "Испеките хлеб"},
            {"armor", "Во всеоружии", "Скуйте броню"}, {"sleep", "Спокойной ночи", "Поспите в кровати"},
            {"lantern", "Да будет свет", "Скрафтите фонарь"}, {"obsi", "Крепче камня", "Добудьте обсидиан"},
            {"nether", "Другое измерение", "Войдите в Нижний мир"},
            {"tnt", "Бабах!", "Взорвите TNT (огниво + ПКМ)"}};
    final java.util.Set<String> ach = new java.util.LinkedHashSet<>();
    boolean achOpen;

    void unlock(String key) {
        if (!survival || ach.contains(key)) return;
        for (String[] a : ACH) if (a[0].equals(key)) {
            ach.add(key);
            msg = "Достижение: " + a[1] + "!";
            msgT = 4;
            snd("chime");
            saveConfig();
            return;
        }
    }

    void achFor(int id) {
        if (id == 6) unlock("wood"); else if (id == I_COAL) unlock("coal"); else if (id == I_INGOT) unlock("iron");
        else if (id == I_GOLD) unlock("gold"); else if (id == I_DIAM) unlock("diamond"); else if (id == I_BREAD) unlock("bread");
        else if (id == I_ARM_I || id == I_ARM_D) unlock("armor"); else if (id == B_LANT) unlock("lantern"); else if (id == B_OBSI) unlock("obsi");
    }

    void addItem(int id, int n) {
        if (id <= 0 || id >= NITEM || n <= 0) return;
        achFor(id);
        if (inv[id] == 0) autoHot(id);
        inv[id] += n;
    }

    void initHotbar() {
        Arrays.fill(hot, 0);
        if (!survival) for (int i = 0; i < 9; i++) hot[i] = PLACE_CREA[i];
        sel = 0;
    }

    /** Рецепт крафта или сделка с жителем. in = {предмет, кол-во, предмет, кол-во...}; tool: 1 кирка, 2 топор, 3 меч. */
    static final class Recipe {
        final String name;
        final int[] in;
        final int out, cnt, tool, tier;

        Recipe(String name, int out, int cnt, int tool, int tier, int... in) {
            this.name = name; this.out = out; this.cnt = cnt; this.tool = tool; this.tier = tier; this.in = in;
        }
    }

    static final Recipe[] RECIPES = {
            new Recipe("Доски x4", 4, 4, 0, 0, 6, 1),
            new Recipe("Палки x4", I_STICK, 4, 0, 0, 4, 2),
            new Recipe("Железный слиток", I_INGOT, 1, 0, 0, I_IRON, 1, I_COAL, 1),
            new Recipe("Деревянная кирка", 0, 0, 1, 1, 4, 3, I_STICK, 2),
            new Recipe("Каменная кирка", 0, 0, 1, 2, 15, 3, I_STICK, 2),
            new Recipe("Железная кирка", 0, 0, 1, 3, I_INGOT, 3, I_STICK, 2),
            new Recipe("Алмазная кирка", 0, 0, 1, 4, I_DIAM, 3, I_STICK, 2),
            new Recipe("Деревянный топор", 0, 0, 2, 1, 4, 3, I_STICK, 2),
            new Recipe("Каменный топор", 0, 0, 2, 2, 15, 3, I_STICK, 2),
            new Recipe("Железный топор", 0, 0, 2, 3, I_INGOT, 3, I_STICK, 2),
            new Recipe("Алмазный топор", 0, 0, 2, 4, I_DIAM, 3, I_STICK, 2),
            new Recipe("Деревянный меч", 0, 0, 3, 1, 4, 2, I_STICK, 1),
            new Recipe("Каменный меч", 0, 0, 3, 2, 15, 2, I_STICK, 1),
            new Recipe("Железный меч", 0, 0, 3, 3, I_INGOT, 2, I_STICK, 1),
            new Recipe("Алмазный меч", 0, 0, 3, 4, I_DIAM, 2, I_STICK, 1),
            new Recipe("Кирпич x4", 13, 4, 0, 0, 20, 4, I_COAL, 1),
            new Recipe("Каменные кирпичи x4", 22, 4, 0, 0, 15, 4),
            new Recipe("Тёмные доски x4", 21, 4, 0, 0, 4, 4, I_COAL, 1),
            new Recipe("Светокамень x2", 19, 2, 0, 0, I_GOLD, 1, I_COAL, 1),
            new Recipe("Сундук", B_CHEST, 1, 0, 0, 4, 8),
            new Recipe("Печка", B_FURN, 1, 0, 0, 15, 8),
            new Recipe("Кровать", B_BED, 1, 0, 0, 18, 3, 4, 3),
            new Recipe("Хлеб", I_BREAD, 1, 0, 0, I_WHEAT, 3),
            new Recipe("Дверь", B_DOOR_L, 1, 0, 0, 4, 6),
            new Recipe("Люк x2", B_TRAP, 2, 0, 0, 4, 4),
            new Recipe("Стекло x4", B_GLASS, 4, 0, 0, 8, 4, I_COAL, 1),
            new Recipe("Книжная полка", B_SHELF, 1, 0, 0, 4, 6, I_WHEAT, 3),
            new Recipe("Обсидиан x2", B_OBSI, 2, 0, 0, 15, 4, I_COAL, 2),
            new Recipe("Красная шерсть x2", B_WOOL_R, 2, 0, 0, 18, 2, I_MEAT, 1),
            new Recipe("Синяя шерсть x2", B_WOOL_B, 2, 0, 0, 18, 2, 20, 1),
            new Recipe("Фонарь x2", B_LANT, 2, 0, 0, I_STICK, 1, I_GOLD, 1, I_COAL, 1),
            new Recipe("Белый бетон x4", 64, 4, 0, 0, 14, 2, 8, 2, I_WHEAT, 1),
            new Recipe("Чёрный бетон x4", 65, 4, 0, 0, 14, 2, 8, 2, I_COAL, 1),
            new Recipe("Красный бетон x4", 66, 4, 0, 0, 14, 2, 8, 2, I_MEAT, 1),
            new Recipe("Синий бетон x4", 67, 4, 0, 0, 14, 2, 8, 2, 20, 1),
            new Recipe("Зелёный бетон x4", 68, 4, 0, 0, 14, 2, 8, 2, I_SEEDS, 2),
            new Recipe("Жёлтый бетон x4", 69, 4, 0, 0, 14, 2, 8, 2, I_WHEAT, 3),
            new Recipe("Оранжевая терракота x4", 70, 4, 0, 0, 20, 4, I_COAL, 1),
            new Recipe("Белая терракота x4", 71, 4, 0, 0, 20, 4, I_WHEAT, 1),
            new Recipe("Полированный камень x4", 72, 4, 0, 0, 15, 4, I_COAL, 1),
            new Recipe("Гладкий кварц x4", 73, 4, 0, 0, B_SANDST, 4, 8, 1),
            new Recipe("Призмарин x4", 74, 4, 0, 0, 15, 2, 20, 2, 8, 1),
            new Recipe("Замшелый булыжник x2", 75, 2, 0, 0, 15, 2, I_SEEDS, 2),
            new Recipe("Колонна x2", 76, 2, 0, 0, 73, 2),
            new Recipe("Золотой блок", 77, 1, 0, 0, I_GOLD, 9),
            new Recipe("Железный блок", 78, 1, 0, 0, I_INGOT, 9),
            new Recipe("Алмазный блок", 79, 1, 0, 0, I_DIAM, 9),
            new Recipe("Угольный блок", 80, 1, 0, 0, I_COAL, 9),
            new Recipe("Паркет x4", 81, 4, 0, 0, 4, 4, I_STICK, 1),
            new Recipe("Светлые доски x4", 82, 4, 0, 0, 4, 4, 8, 1),
            new Recipe("Шахматная плитка x4", 83, 4, 0, 0, 22, 2, 21, 2),
            new Recipe("Лампа x2", 84, 2, 0, 0, B_GLASS, 2, 19, 1),
            new Recipe("TNT", B_TNT, 1, 0, 0, I_COAL, 4, 8, 4),
            new Recipe("Золотой хлеб", I_GAPPLE, 1, 0, 0, I_BREAD, 1, I_GOLD, 4),
            new Recipe("Огниво", I_FLINT, 1, 0, 0, I_INGOT, 1, I_COAL, 1),
            new Recipe("Железная броня", I_ARM_I, 1, 0, 0, I_INGOT, 8),
            new Recipe("Алмазная броня", I_ARM_D, 1, 0, 0, I_DIAM, 8),
    };

    static final Recipe[] TRADES = {
            new Recipe("Золото x1 за мясо", I_GOLD, 1, 0, 0, I_MEAT, 4),
            new Recipe("Золото x1 за уголь", I_GOLD, 1, 0, 0, I_COAL, 6),
            new Recipe("Золото x2 за слитки", I_GOLD, 2, 0, 0, I_INGOT, 2),
            new Recipe("Золото x5 за алмаз", I_GOLD, 5, 0, 0, I_DIAM, 1),
            new Recipe("Палки x6", I_STICK, 6, 0, 0, I_GOLD, 1),
            new Recipe("Шерсть x4", 18, 4, 0, 0, I_GOLD, 1),
            new Recipe("Каменные кирпичи x8", 22, 8, 0, 0, I_GOLD, 1),
            new Recipe("Железные слитки x2", I_INGOT, 2, 0, 0, I_GOLD, 2),
            new Recipe("Светокамень x4", 19, 4, 0, 0, I_GOLD, 3),
            new Recipe("Алмаз x1", I_DIAM, 1, 0, 0, I_GOLD, 5),
            new Recipe("Семена x8", I_SEEDS, 8, 0, 0, I_GOLD, 1),
            new Recipe("Хлеб x3", I_BREAD, 3, 0, 0, I_GOLD, 1),
    };

    enum State { SPLASH, MENU, PLAYING, PAUSED, DEAD }

    // ====================================================================
    //  Хэши и шум
    // ====================================================================
    static double hash2(int x, int z, int seed) {
        int h = x * 374761393 + z * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    static double noise(double x, double z, int seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash2(xi, zi, seed), b = hash2(xi + 1, zi, seed);
        double c = hash2(xi, zi + 1, seed), d = hash2(xi + 1, zi + 1, seed);
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    static double hash3(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 1274126177 + z * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    static double noise3(double x, double y, double z, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double fx = x - xi, fy = y - yi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash3(xi, yi, zi, seed), b = hash3(xi + 1, yi, zi, seed);
        double c = hash3(xi, yi + 1, zi, seed), d = hash3(xi + 1, yi + 1, zi, seed);
        double e = hash3(xi, yi, zi + 1, seed), f = hash3(xi + 1, yi, zi + 1, seed);
        double g = hash3(xi, yi + 1, zi + 1, seed), h = hash3(xi + 1, yi + 1, zi + 1, seed);
        double x0 = a + (b - a) * fx, x1 = c + (d - c) * fx, x2 = e + (f - e) * fx, x3 = g + (h - g) * fx;
        double y0 = x0 + (x1 - x0) * fy, y1 = x2 + (x3 - x2) * fy;
        return y0 + (y1 - y0) * fz;
    }

    static int ih(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h;
    }

    static double frac(double v) { return v - Math.floor(v); }

    static double clamp01(double v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    static int mix(int c1, int c2, double k) {
        int r = (int) (((c1 >> 16) & 255) * (1 - k) + ((c2 >> 16) & 255) * k);
        int g = (int) (((c1 >> 8) & 255) * (1 - k) + ((c2 >> 8) & 255) * k);
        int b = (int) ((c1 & 255) * (1 - k) + (c2 & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    static int scale(int c, double k) {
        return (Math.min(255, (int) (((c >> 16) & 255) * k)) << 16)
                | (Math.min(255, (int) (((c >> 8) & 255) * k)) << 8)
                | Math.min(255, (int) ((c & 255) * k));
    }

    /** Пересечение луча с коробкой. Возвращает расстояние или -1. ax[0]=ось, ax[1]=знак нормали. */
    static double rayBox(double ox, double oy, double oz, double dx, double dy, double dz,
                         double x0, double y0, double z0, double x1, double y1, double z1, int[] ax) {
        double tmin = 0, tmax = 1e30;
        int axis = 0, sg = 0;
        if (dx != 0) {
            double inv = 1 / dx, ta = (x0 - ox) * inv, tb = (x1 - ox) * inv;
            if (ta > tb) { double s = ta; ta = tb; tb = s; }
            if (ta > tmin) { tmin = ta; axis = 0; sg = dx > 0 ? -1 : 1; }
            if (tb < tmax) tmax = tb;
            if (tmin > tmax) return -1;
        } else if (ox < x0 || ox > x1) return -1;
        if (dy != 0) {
            double inv = 1 / dy, ta = (y0 - oy) * inv, tb = (y1 - oy) * inv;
            if (ta > tb) { double s = ta; ta = tb; tb = s; }
            if (ta > tmin) { tmin = ta; axis = 1; sg = dy > 0 ? -1 : 1; }
            if (tb < tmax) tmax = tb;
            if (tmin > tmax) return -1;
        } else if (oy < y0 || oy > y1) return -1;
        if (dz != 0) {
            double inv = 1 / dz, ta = (z0 - oz) * inv, tb = (z1 - oz) * inv;
            if (ta > tb) { double s = ta; ta = tb; tb = s; }
            if (ta > tmin) { tmin = ta; axis = 2; sg = dz > 0 ? -1 : 1; }
            if (tb < tmax) tmax = tb;
            if (tmin > tmax) return -1;
        } else if (oz < z0 || oz > z1) return -1;
        if (tmax < 0) return -1;
        ax[0] = axis;
        ax[1] = sg;
        return tmin;
    }

    // ====================================================================
    //  Мир из чанков
    // ====================================================================
    static final class Chunk {
        final int cx, cz;
        final byte[] b;
        final byte[] hm = new byte[CS * CS];                  // высота самого верхнего блока, закрывающего небо
        volatile boolean modified;
        volatile byte[] bl;                                   // блочный свет 0..15 (null - в чанке и рядом нет источников)
        int emit;                                             // сколько светящихся блоков в чанке

        Chunk(int cx, int cz, byte[] b) { this.cx = cx; this.cz = cz; this.b = b; computeH(); countEmit(); }

        void countEmit() {
            int n = 0;
            for (byte v : b) if (emits(v)) n++;
            emit = n;
        }

        /** Закрывает ли блок небо (вода и листва - нет). */
        static boolean thru(int id) { return id == B_GLASS || id == B_PORTAL || id == B_DOOR_LO || id == B_DOOR_UO || id == B_TRAP_O; }

        static boolean occ(int id) { return id != 0 && id != WATER && id != 5 && !thru(id) && id != B_CROP && id != B_RIPE; }

        void computeH() {
            for (int z = 0; z < CS; z++) {
                for (int x = 0; x < CS; x++) {
                    int y = WY - 1;
                    while (y > 0 && !occ(b[(y * CS + z) * CS + x])) y--;
                    hm[z * CS + x] = (byte) y;
                }
            }
        }
    }

    /** Сундук (27 ячеек) или печка (вход, топливо, результат). Живёт на хосте. */
    static final class Tile {
        final int type;
        final int[] item, cnt;
        int x, y, z;
        double burn, burnMax, prog;

        Tile(int type) {
            this.type = type;
            int n = type == B_FURN ? 3 : 27;
            item = new int[n];
            cnt = new int[n];
        }
    }

    static long tkey(int x, int y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF); }

    static final class World {
        final int seed, R;
        final boolean saver;                                  // true = не генерировать то, что позади
        final Chunk[] ring = new Chunk[RING * RING];          // быстрый доступ для рендера
        final HashMap<Long, Chunk> loaded = new HashMap<>();  // только под synchronized
        final ConcurrentHashMap<Long, Chunk> saved = new ConcurrentHashMap<>();  // изменённые выгруженные чанки
        volatile double fx, fz, dirx, dirz = 1;
        volatile boolean run = true;
        volatile int count;
        Thread th;
        final int s1, s2, s3;
        final ConcurrentHashMap<Long, Villages.Village> vcache = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Long, Tile> tiles = new ConcurrentHashMap<>();       // содержимое сундуков и печек
        final ConcurrentHashMap<Long, int[]> crops = new ConcurrentHashMap<>();      // растущая пшеница {x,y,z}

        void scanCrops(Chunk c) {
            for (int y = 1; y < WY - 1; y++)
                for (int lz = 0; lz < CS; lz++)
                    for (int lx = 0; lx < CS; lx++)
                        if (c.b[(y * CS + lz) * CS + lx] == B_CROP) {
                            int wx = c.cx * CS + lx, wz = c.cz * CS + lz;
                            crops.put(tkey(wx, y, wz), new int[]{wx, y, wz});
                        }
        }

        /** Контейнер в этой клетке (создаётся пустым при первом обращении), или null, если там не контейнер. */
        Tile getTile(int x, int y, int z) {
            int id = get(x, y, z);
            if (id != B_CHEST && id != B_FURN) return null;
            long k = tkey(x, y, z);
            Tile t = tiles.get(k);
            if (t == null || t.type != id) {
                t = new Tile(id);
                t.x = x; t.y = y; t.z = z;
                tiles.put(k, t);
            }
            return t;
        }

        World(int seed, int R, boolean saver) {
            this.seed = seed;
            this.R = R;
            this.saver = saver;
            s1 = seed * 7 + 1;
            s2 = seed * 7 + 2;
            s3 = seed * 7 + 3;
        }

        static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

        Chunk chunk(int cx, int cz) {
            Chunk c = ring[(cz & (RING - 1)) * RING + (cx & (RING - 1))];
            return (c != null && c.cx == cx && c.cz == cz) ? c : null;
        }

        boolean biomes = true;

        /** 0 равнины, 1 пустыня, 2 снежный лес. */
        int biome(int x, int z) {
            if (!biomes) return 0;
            double t = noise(x / 240.0 + 100, z / 240.0 - 60, s1 + 77);
            return t > BIO_D ? 1 : t < BIO_S ? 2 : 0;
        }

        int heightAt(int x, int z) {
            double hh = 14 + noise(x / 90.0, z / 90.0, s1) * 22
                    + noise(x / 26.0, z / 26.0, s2) * 8
                    + noise(x / 8.0, z / 8.0, s3) * 2;
            return (int) hh;
        }

        /** Блок или -1, если чанк не загружен. */
        int get(int x, int y, int z) {
            if (y < 0 || y >= WY) return 0;
            Chunk c = chunk(x >> 4, z >> 4);
            if (c == null) return -1;
            return c.b[(y * CS + (z & 15)) * CS + (x & 15)];
        }

        boolean set(int x, int y, int z, int id) {
            if (y < 0 || y >= WY) return false;
            Chunk c = chunk(x >> 4, z >> 4);
            if (c == null) return false;
            int bi = (y * CS + (z & 15)) * CS + (x & 15);
            int old = c.b[bi];
            c.b[bi] = (byte) id;
            c.modified = true;
            if (id == B_CROP) crops.put(tkey(x, y, z), new int[]{x, y, z});
            if (old != id) {
                if (emits(old)) c.emit--;
                if (emits(id)) c.emit++;
                markLight(c.cx, c.cz);
            }
            int li = (z & 15) * CS + (x & 15);
            if (Chunk.occ(id)) {
                if (y > c.hm[li]) c.hm[li] = (byte) y;
            } else if (y == c.hm[li]) {
                int yy = y - 1;
                while (yy > 0 && !Chunk.occ(c.b[(yy * CS + (z & 15)) * CS + (x & 15)])) yy--;
                c.hm[li] = (byte) Math.max(0, yy);
            }
            return true;
        }

        /** Твёрдый ли блок (воздух и вода - нет). */
        boolean solid(int x, int y, int z) {
            if (y < 0) return true;
            if (y >= WY) return false;
            Chunk c = chunk(x >> 4, z >> 4);
            if (c == null) return true;                       // незагруженное считаем твёрдым
            int id = c.b[(y * CS + (z & 15)) * CS + (x & 15)];
            return id != 0 && id != WATER && !(id < 32 && SHORT_H[id] > 0) && id != B_DOOR_LO && id != B_DOOR_UO && id != B_TRAP_O && id != B_PORTAL;       // кровать и всходы проходимы
        }

        int hmapAt(int x, int z) {
            Chunk c = chunk(x >> 4, z >> 4);
            return c == null ? -1000 : c.hm[(z & 15) * CS + (x & 15)];
        }

        boolean isWater(double x, double y, double z) {
            return get((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)) == WATER;
        }

        static final double CAVE_R2 = 0.0013, CAVERN_T = 0.84, COAL_T = 0.85, IRON_T = 0.88, GOLD_T = 0.92, DIAM_T = 0.94;

        /** Вырезан ли блок пещерой (чистая функция координат - чанки сходятся на границах). */
        boolean cave(int x, int y, int z, int h) {
            if (y < 3) return false;
            int top = h > SEA + 3 ? h : h - 4;
            if (y > top) return false;
            double a = noise3(x * 0.05, y * 0.08, z * 0.05, s1 + 11) - 0.5;
            double b = noise3(x * 0.05 + 37.1, y * 0.08 + 11.3, z * 0.05 + 91.7, s1 + 13) - 0.5;
            if (a * a + b * b < CAVE_R2) return true;
            return y < 30 && noise3(x * 0.028, y * 0.05, z * 0.028, s2 + 17) > CAVERN_T;
        }

        int surfaceY(int x, int z) {
            for (int y = WY - 1; y >= 0; y--) {
                int b = get(x, y, z);
                if (b < 0) return -1;
                if (b != 0) return y;
            }
            return -1;
        }

        // ---------- генерация ----------
        static void put(byte[] b, int ox, int oz, int wx, int y, int wz, int id, boolean force) {
            int lx = wx - ox, lz = wz - oz;
            if (lx < 0 || lz < 0 || lx >= CS || lz >= CS || y < 0 || y >= WY) return;
            int i = (y * CS + lz) * CS + lx;
            if (force || b[i] == 0) b[i] = (byte) id;
        }

        Chunk genNether(int cx, int cz) {
            byte[] b = new byte[CS * WY * CS];
            for (int lz = 0; lz < CS; lz++)
                for (int lx = 0; lx < CS; lx++) {
                    int wx = cx * CS + lx, wz = cz * CS + lz;
                    for (int y = 0; y < WY; y++) {
                        int id = 0;
                        if (y == 0 || y == WY - 1) id = 7;
                        else {
                            double n = noise3(wx * 0.045, y * 0.075, wz * 0.045, s2 + 3);
                            double bias = Math.pow(Math.abs(y - 32) / 31.0, 2.2) * 0.55;
                            boolean solid = y <= 2 || y >= WY - 3 || n + bias > 0.6;
                            if (solid) {
                                id = B_NRACK;
                                int hh = ih(wx * 7 + y, wz * 13, y * 3 + 5) & 255;
                                if (y >= 48 && y < WY - 2 && hh < 4) id = B_GLOW;
                                else if (hh > 250 && y < 50 && y > 3) id = B_GOLD;
                            } else if (y <= 9) id = B_MAGMA;
                        }
                        b[(y * CS + lz) * CS + lx] = (byte) id;
                    }
                }
            return new Chunk(cx, cz, b);
        }

        Chunk generate(int cx, int cz) {
            if (cx * CS >= NT) return genNether(cx, cz);
            byte[] b = new byte[CS * WY * CS];
            int ox = cx * CS, oz = cz * CS;
            for (int lz = 0; lz < CS; lz++) {
                for (int lx = 0; lx < CS; lx++) {
                    int wx = ox + lx, wz = oz + lz;
                    int h = heightAt(wx, wz);
                    int bio = biome(wx, wz);
                    boolean desert = bio == 1 && h > SEA - 2;
                    boolean sand = h <= SAND_H || desert, snow = !sand && (h >= SNOW_H || (bio == 2 && h > SAND_H));
                    boolean seabed = h < SEA;
                    for (int y = 0; y <= h; y++) {
                        int id;
                        if (y == 0) id = 7;
                        else if (y == h) {
                            id = sand ? 8 : snow ? 9 : 1;
                            if (seabed && noise(wx / 7.0, wz / 7.0, s3 + 5) > 0.62) id = 20;      // глина на дне
                            else if (seabed && noise(wx / 6.0, wz / 6.0, s2 + 9) > 0.66) id = 14; // гравий на дне
                        } else if (y >= h - 3) id = sand ? 8 : 2;
                        else if (desert && y >= h - 7) id = B_SANDST;
                        else {
                            id = 3;
                            if (y < h - 1) {
                                if (noise3(wx * 0.16, y * 0.16, wz * 0.16, s2 + 5) > COAL_T) id = B_COAL;
                                else if (y < 34 && noise3(wx * 0.19 + 50, y * 0.19, wz * 0.19, s3 + 9) > IRON_T) id = B_IRON;
                                else if (y < 18 && noise3(wx * 0.22 + 90, y * 0.22, wz * 0.22, s1 + 21) > GOLD_T) id = B_GOLD;
                                else if (y < 12 && noise3(wx * 0.25 + 17, y * 0.25, wz * 0.25 + 40, s1 + 31) > DIAM_T) id = B_DIAM;
                            }
                        }
                        if (y >= 3 && cave(wx, y, wz, h)) id = 0;
                        b[(y * CS + lz) * CS + lx] = (byte) id;
                    }
                    for (int y = h + 1; y <= SEA; y++) b[(y * CS + lz) * CS + lx] = (byte) WATER;
                }
            }
            // Деревья: смотрим на кандидатов с запасом 2 блока, чтобы кроны на границах чанков сходились
            for (int wx = ox - 2; wx < ox + CS + 2; wx++) {
                for (int wz = oz - 2; wz < oz + CS + 2; wz++) {
                    int bio = biome(wx, wz);
                    if (hash2(wx, wz, seed + 991) >= (bio == 1 ? 0 : bio == 2 ? 0.022 : 0.012)) continue;
                    int h = heightAt(wx, wz);
                    if (h <= SAND_H || (h >= SNOW_H && bio != 2) || cave(wx, h, wz, h) || Villages.near(this, wx, wz, 38)) continue;
                    int th = 4 + (int) (hash2(wx, wz, seed + 17) * 3);
                    for (int y = h + 1; y <= h + th; y++) put(b, ox, oz, wx, y, wz, 6, true);
                    for (int dy = th - 1; dy <= th + 2; dy++) {
                        int rad = dy >= th + 1 ? 1 : 2;
                        for (int dx = -rad; dx <= rad; dx++) {
                            for (int dz = -rad; dz <= rad; dz++) {
                                if (rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                                put(b, ox, oz, wx + dx, h + dy, wz + dz, 5, false);
                            }
                        }
                    }
                }
            }
            for (int lz = 0; lz < CS; lz++)                      // кактусы
                for (int lx = 0; lx < CS; lx++) {
                    int wx = ox + lx, wz = oz + lz;
                    if (biome(wx, wz) != 1 || hash2(wx, wz, seed + 313) >= 0.007) continue;
                    int h = heightAt(wx, wz);
                    if (h <= SEA + 1 || cave(wx, h, wz, h) || Villages.near(this, wx, wz, 38)) continue;
                    int ch = 1 + (int) (hash2(wx, wz, seed + 77) * 3);
                    for (int y = h + 1; y <= h + ch; y++) put(b, ox, oz, wx, y, wz, B_CACTUS, true);
                }
            Villages.apply(this, b, cx, cz);
            return new Chunk(cx, cz, b);
        }

        // ---------- блочный свет (светокамень, фонари) ----------
        final java.util.Set<Long> dirty = ConcurrentHashMap.newKeySet();   // чанки, у которых надо пересчитать свет

        /** Есть ли источники света в самом чанке или рядом (в 3x3), либо остался старый свет. */
        boolean nearEmit(Chunk n) {
            if (n.bl != null) return true;
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    Chunk m = chunk(n.cx + dx, n.cz + dz);
                    if (m != null && m.emit > 0) return true;
                }
            return false;
        }

        /** Отмечает чанк и соседей на пересчёт света. */
        void markLight(int cx, int cz) {
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    Chunk n = chunk(cx + dx, cz + dz);
                    if (n != null && nearEmit(n)) dirty.add(key(n.cx, n.cz));
                }
        }

        int blightAt(int x, int y, int z) {
            if (y < 0 || y >= WY) return 0;
            Chunk c = chunk(x >> 4, z >> 4);
            if (c == null) return 0;
            byte[] l = c.bl;
            return l == null ? 0 : l[(y * CS + (z & 15)) * CS + (x & 15)];
        }

        static boolean clear(int id) { return id == 0 || id == WATER || id == 5 || Chunk.thru(id) || id == B_CROP || id == B_RIPE; }

        /** Заливка света от светящихся блоков в окне 3x3 чанков; сохраняется только середина. */
        void relight(Chunk c) {
            final int W = CS * 3;
            Chunk[] nb = new Chunk[9];
            boolean any = false;
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    Chunk m = chunk(c.cx + dx, c.cz + dz);
                    nb[(dz + 1) * 3 + dx + 1] = m;
                    if (m != null && m.emit > 0) any = true;
                }
            if (!any) { c.bl = null; return; }
            byte[] lv = new byte[WY * W * W];
            int[] q = new int[1 << 13];
            int qh = 0, qt = 0;
            for (int i = 0; i < 9; i++) {
                Chunk m = nb[i];
                if (m == null || m.emit <= 0) continue;
                int offx = (i % 3) * CS, offz = (i / 3) * CS;
                for (int y = 0; y < WY; y++)
                    for (int lz = 0; lz < CS; lz++)
                        for (int lx = 0; lx < CS; lx++) {
                            if (!emits(m.b[(y * CS + lz) * CS + lx])) continue;
                            int idx = (y * W + offz + lz) * W + offx + lx;
                            lv[idx] = 15;
                            if (qt == q.length) q = Arrays.copyOf(q, q.length * 2);
                            q[qt++] = idx;
                        }
            }
            final int[] dxs = {1, -1, 0, 0, 0, 0}, dys = {0, 0, 1, -1, 0, 0}, dzs = {0, 0, 0, 0, 1, -1};
            while (qh < qt) {
                int idx = q[qh++];
                int l = lv[idx];
                if (l <= 1) continue;
                int xl = idx % W, zl = (idx / W) % W, y = idx / (W * W);
                for (int d = 0; d < 6; d++) {
                    int nx = xl + dxs[d], ny = y + dys[d], nz = zl + dzs[d];
                    if (nx < 0 || nz < 0 || nx >= W || nz >= W || ny < 0 || ny >= WY) continue;
                    int ni = (ny * W + nz) * W + nx;
                    if (lv[ni] >= l - 1) continue;
                    Chunk m = nb[(nz >> 4) * 3 + (nx >> 4)];
                    if (m == null || !clear(m.b[(ny * CS + (nz & 15)) * CS + (nx & 15)])) continue;
                    lv[ni] = (byte) (l - 1);
                    if (qt == q.length) q = Arrays.copyOf(q, q.length * 2);
                    q[qt++] = ni;
                }
            }
            byte[] out = new byte[CS * WY * CS];
            boolean nz = false;
            for (int y = 0; y < WY; y++)
                for (int lz = 0; lz < CS; lz++)
                    for (int lx = 0; lx < CS; lx++) {
                        byte v = lv[(y * W + CS + lz) * W + CS + lx];
                        out[(y * CS + lz) * CS + lx] = v;
                        if (v != 0) nz = true;
                    }
            c.bl = nz ? out : null;
        }

        static volatile int RELIGHT_N = 4;

        private void relightSome() {
            int n = 0;
            for (Iterator<Long> it = dirty.iterator(); it.hasNext() && n < RELIGHT_N; ) {
                Long k = it.next();
                it.remove();
                Chunk c = loaded.get(k);
                if (c != null) { relight(c); n++; }
            }
        }

        // ---------- загрузка / выгрузка ----------
        private void ensure(int cx, int cz) {
            long k = key(cx, cz);
            if (loaded.containsKey(k)) return;
            Chunk c = saved.remove(k);
            if (c == null) c = generate(cx, cz);
            List<int[]> pl = patches.remove(k);
            if (pl != null) {
                for (int[] e : pl) c.b[(e[1] * CS + (e[2] & 15)) * CS + (e[0] & 15)] = (byte) e[3];
                c.modified = true;
                c.computeH();
                c.countEmit();
            }
            loaded.put(k, c);
            ring[(cz & (RING - 1)) * RING + (cx & (RING - 1))] = c;
            scanCrops(c);
            markLight(cx, cz);
            count = loaded.size();
        }

        synchronized void loadAround(int rad) {
            int pcx = ((int) Math.floor(fx)) >> 4, pcz = ((int) Math.floor(fz)) >> 4;
            for (int dz = -rad; dz <= rad; dz++)
                for (int dx = -rad; dx <= rad; dx++)
                    ensure(pcx + dx, pcz + dz);
        }

        private boolean wanted(int cx, int cz, double px, double pz, double ldx, double ldz, boolean load) {
            double rx = cx * CS + 8 - px, rz = cz * CS + 8 - pz;
            double d = Math.sqrt(rx * rx + rz * rz);
            if (d <= 40) return true;                         // рядом с игроком всегда
            if (d > (load ? R * CS : (R + 1) * CS)) return false;
            if (!saver) return true;
            double dot = (rx * ldx + rz * ldz) / d;           // cos угла между взглядом и чанком
            return dot > (load ? 0.15 : -0.3);                // загружаем впереди, выгружаем сильно позади
        }

        /** Дополнительные игроки сетевой игры: {x, z, dirx, dirz}. Вокруг них тоже держатся чанки. */
        volatile double[][] extra = new double[0][];

        /** Чужие правки блоков в ещё не загруженных чанках: применяются, когда чанк появится. */
        final ConcurrentHashMap<Long, List<int[]>> patches = new ConcurrentHashMap<>();

        /** Меняет блок где угодно: в загруженном чанке, в выгруженном (сохранённом) или откладывает правку. */
        synchronized void edit(int x, int y, int z, int id) {
            if (y < 0 || y >= WY) return;
            int cx = x >> 4, cz = z >> 4;
            if (set(x, y, z, id)) return;
            Chunk c = saved.get(key(cx, cz));
            if (c != null) {
                c.b[(y * CS + (z & 15)) * CS + (x & 15)] = (byte) id;
                c.modified = true;
                c.computeH();
                c.countEmit();
                return;
            }
            patches.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(new int[]{x, y, z, id});
        }

        private boolean wantedAny(int cx, int cz, List<double[]> an, boolean load) {
            for (double[] a : an) if (wanted(cx, cz, a[0], a[1], a[2], a[3], load)) return true;
            return false;
        }

        synchronized void tick() {
            List<double[]> an = new ArrayList<>();
            an.add(new double[]{fx, fz, dirx, dirz});
            for (double[] e : extra) an.add(e);
            Iterator<Chunk> it = loaded.values().iterator();
            while (it.hasNext()) {
                Chunk c = it.next();
                if (!wantedAny(c.cx, c.cz, an, false)) {
                    it.remove();
                    int idx = (c.cz & (RING - 1)) * RING + (c.cx & (RING - 1));
                    if (ring[idx] == c) ring[idx] = null;
                    if (c.modified) saved.put(key(c.cx, c.cz), c);
                }
            }
            List<int[]> cand = new ArrayList<>();
            java.util.HashSet<Long> seen = new java.util.HashSet<>();
            for (double[] a : an) {
                int pcx = ((int) Math.floor(a[0])) >> 4, pcz = ((int) Math.floor(a[1])) >> 4;
                for (int dz = -R; dz <= R; dz++) {
                    for (int dx = -R; dx <= R; dx++) {
                        int cx = pcx + dx, cz = pcz + dz;
                        long k = key(cx, cz);
                        if (loaded.containsKey(k) || !seen.add(k)) continue;
                        if (!wanted(cx, cz, a[0], a[1], a[2], a[3], true)) continue;
                        double rx = cx * CS + 8 - a[0], rz = cz * CS + 8 - a[1];
                        cand.add(new int[]{cx, cz, (int) (rx * rx + rz * rz)});
                    }
                }
            }
            cand.sort((p, q) -> Integer.compare(p[2], q[2]));
            for (int i = 0; i < Math.min(6 + 2 * an.size(), cand.size()); i++) ensure(cand.get(i)[0], cand.get(i)[1]);
            relightSome();
            count = loaded.size();
        }

        /** Изменённые игроком чанки: загруженные и уже выгруженные. */
        synchronized List<Chunk> modifiedChunks() {
            List<Chunk> l = new ArrayList<>();
            for (Chunk c : loaded.values()) if (c.modified) l.add(c);
            l.addAll(saved.values());
            return l;
        }

        void start() {
            th = new Thread(() -> {
                while (run) {
                    try {
                        tick();
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "chunk-loader");
            th.setDaemon(true);
            th.setPriority(Thread.MIN_PRIORITY);
            th.start();
        }

        void stop() {
            run = false;
            if (th != null) th.interrupt();
        }

        /** Луч через воксели (DDA). o = {x,y,z, нормаль xyz, id блока}. Возвращает расстояние или -1. */
        double cast(double ox, double oy, double oz, double dx, double dy, double dz, double max, int[] o, double[] wd) {
            return cast(ox, oy, oz, dx, dy, dz, max, o, wd, null);
        }

        /** gl != null: стекло прозрачно для луча, а первое пересечённое стекло записывается в gl {расстояние, ось}. */
        double cast(double ox, double oy, double oz, double dx, double dy, double dz, double max, int[] o, double[] wd, double[] gl) {
            int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
            wd[0] = 0;
            wd[1] = -1;
            boolean inW = false;
            double ws = 0, lastT = 0, result = -1;
            int stx = dx > 0 ? 1 : -1, sty = dy > 0 ? 1 : -1, stz = dz > 0 ? 1 : -1;
            double tdx = dx == 0 ? 1e30 : Math.abs(1.0 / dx);
            double tdy = dy == 0 ? 1e30 : Math.abs(1.0 / dy);
            double tdz = dz == 0 ? 1e30 : Math.abs(1.0 / dz);
            double tmx = dx == 0 ? 1e30 : (dx > 0 ? x + 1 - ox : ox - x) * tdx;
            double tmy = dy == 0 ? 1e30 : (dy > 0 ? y + 1 - oy : oy - y) * tdy;
            double tmz = dz == 0 ? 1e30 : (dz > 0 ? z + 1 - oz : oz - z) * tdz;
            Chunk cc = chunk(x >> 4, z >> 4);
            int ccx = x >> 4, ccz = z >> 4;
            if (cc != null && y >= 0 && y < WY && cc.b[(y * CS + (z & 15)) * CS + (x & 15)] == WATER) {
                inW = true;                                   // камера под водой
                wd[1] = 0;
            }
            while (true) {
                int fx, fy, fz;
                double t;
                if (tmx < tmy && tmx < tmz) { t = tmx; tmx += tdx; x += stx; fx = -stx; fy = 0; fz = 0; }
                else if (tmy < tmz) { t = tmy; tmy += tdy; y += sty; fx = 0; fy = -sty; fz = 0; }
                else { t = tmz; tmz += tdz; z += stz; fx = 0; fy = 0; fz = -stz; }
                if (t > max) { lastT = max; break; }
                lastT = t;
                if (y < 0) break;
                if (y >= WY) { if (sty > 0) break; continue; }
                int cx = x >> 4, cz = z >> 4;
                if (cx != ccx || cz != ccz) { cc = chunk(cx, cz); ccx = cx; ccz = cz; }
                if (cc == null) break;                        // дальше мир не сгенерирован
                int bl = cc.b[(y * CS + (z & 15)) * CS + (x & 15)];
                if (bl == WATER) {
                    if (!inW) { inW = true; ws = t; if (wd[1] < 0) wd[1] = t; }
                    continue;
                }
                if (gl != null && Chunk.thru(bl)) {
                    if (gl[0] < 0) { gl[0] = t; gl[1] = fx != 0 ? 0 : fy != 0 ? 1 : 2; gl[2] = bl; }
                    continue;
                }
                if (inW) { wd[0] += t - ws; inW = false; }
                if (bl != 0) {
                    if (bl > 0 && bl < 32 && SHORT_H[bl] > 0) {                 // низкий блок: проверяем попадание в его коробку
                        int[] axb = new int[2];
                        double tt = rayBox(ox, oy, oz, dx, dy, dz, x, y, z, x + 1, y + SHORT_H[bl], z + 1, axb);
                        if (tt < 0) continue;
                        if (tt > max) break;
                        o[0] = x; o[1] = y; o[2] = z; o[6] = bl;
                        o[3] = axb[0] == 0 ? axb[1] : 0; o[4] = axb[0] == 1 ? axb[1] : 0; o[5] = axb[0] == 2 ? axb[1] : 0;
                        return tt;
                    }
                    o[0] = x; o[1] = y; o[2] = z; o[3] = fx; o[4] = fy; o[5] = fz; o[6] = bl;
                    return t;
                }
            }
            if (inW) wd[0] += lastT - ws;
            return result;
        }
    }

    // ====================================================================
    //  Тела (игрок, зомби)
    // ====================================================================
    boolean boxHits(double x, double y, double z, double hw, double h) {
        World w = world;
        int x0 = (int) Math.floor(x - hw), x1 = (int) Math.floor(x + hw - 1e-9);
        int y0 = (int) Math.floor(y), y1 = (int) Math.floor(y + h - 1e-9);
        int z0 = (int) Math.floor(z - hw), z1 = (int) Math.floor(z + hw - 1e-9);
        for (int xx = x0; xx <= x1; xx++)
            for (int yy = y0; yy <= y1; yy++)
                for (int zz = z0; zz <= z1; zz++)
                    if (w.solid(xx, yy, zz)) return true;
        return false;
    }

    class Body {
        double x, y, z, vx, vy, vz, impact;
        final double hw, h;
        boolean onGround, blocked;

        Body(double hw, double h) { this.hw = hw; this.h = h; }

        boolean hit() { return boxHits(x, y, z, hw, h); }

        void step(double dt) {
            int steps = 3;
            double s = dt / steps;
            boolean g = false;
            blocked = false;
            impact = 0;
            for (int i = 0; i < steps; i++) {
                x += vx * s; if (hit()) { x -= vx * s; if (vx != 0) blocked = true; }
                z += vz * s; if (hit()) { z -= vz * s; if (vz != 0) blocked = true; }
                double dy = vy * s;
                y += dy;
                if (hit()) {
                    y -= dy;
                    if (dy < 0) { g = true; if (vy < impact) impact = vy; }
                    vy = 0;
                }
            }
            onGround = g;
        }
    }

    class Mob extends Body {
        final int type;
        double hp, hurt, atk = 0.8, kx, kz;
        double aiT, fleeT, ang, dirA, phase, swing, sndT, homeX, homeZ;
        boolean moving;
        int village = -1;
        int id;                                               // сетевой номер моба
        double nx, ny, nz, tang;                              // целевая позиция и угол (у клиента, для плавного движения)

        Mob(int type) {
            super(MobModels.HW[type], MobModels.H[type]);
            this.type = type;
            this.id = ++mobSeq;
            hp = MobModels.HP[type];
            ang = dirA = rnd.nextDouble() * Math.PI * 2;
            aiT = rnd.nextDouble() * 3;
            sndT = 4 + rnd.nextDouble() * 8;
        }

        boolean hostile() { return type == MobModels.ZOMBIE; }
    }

    /** Снимок моба для рендера (чтобы параллельные потоки не читали изменяемые данные). */
    static final class MobView {
        int[] skin;
        int headIdx = -1;
        double hs, hc = 1, pivY = 1.35;
        double mx, my, mz, c, s, rad, top, lit;
        double[] box;
        MobModels.Part[] parts;
        boolean hurt;
    }

    static final class Btn {
        String t;
        int x, y, w, h;
        Runnable a, r;

        boolean contains(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    // ====================================================================
    //  Состояние игры
    // ====================================================================
    final Object lock = new Object();
    final Random rnd = new Random();
    State state = State.SPLASH;
    double splashT;

    World world, menuWorld, gameWorld;
    int menuSeed, viewR = 6;
    boolean saver = true, survival, fly, captured, showInfo, lmb;
    double orbitYaw, orbitX, orbitY, orbitZ;

    final Body pl = new Body(0.3, 1.8);
    double yaw, pitch, kbx, kbz;
    int sel, hp = MAXHP;
    int pick, axe, sword;                                     // уровни инструментов 0..4
    double air = 10, drownT, waterT;
    boolean wasInW;
    final int[] inv = new int[NITEM];
    final int[] hot = new int[9];                             // что лежит в ячейках панели быстрого доступа (0 - пусто)
    double hurtT, regenT, atkCd, mineT, spawnT, spawnX, spawnY, spawnZ;
    String msg = "";
    double msgT;

    boolean mining;
    int mineX, mineY, mineZ;
    double mineProg;

    boolean hasTarget;
    int tx, ty, tz, tnx, tny, tnz;
    Mob targetMob;
    RPlayer targetPl;
    final List<Mob> mobs = new ArrayList<>();

    double tod = 0.35, daylight = 1;
    int skyTopC, skyHorC;
    double sunX, sunY, sunZ;

    final boolean[] keys = new boolean[256];
    Robot robot;
    int cx, cy, mpx, mpy, fps;

    BufferedImage img = new BufferedImage(RW, RH, BufferedImage.TYPE_INT_RGB);
    int[] pix = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

    void setRes(int w, int h) {
        if (w == RW && h == RH) return;
        RW = w; RH = h;
        img = new BufferedImage(RW, RH, BufferedImage.TYPE_INT_RGB);
        pix = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }

    java.util.concurrent.ForkJoinPool rpool;

    void runRender(Runnable job) {
        if (rpool == null) job.run(); else rpool.submit(job).join();
    }

    // ====================================================================
    //  Запуск игры, меню
    // ====================================================================
    MiniCraft() {
        setPreferredSize(new Dimension(1280, 720));
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        menuSeed = 100000 + rnd.nextInt(900000);
        initOpts();
        loadConfig();
        loadAccounts();
        if (!skinPath.trim().isEmpty()) loadMySkin();
        if (myUid.length() < 8) { myUid = java.util.UUID.randomUUID().toString(); saveConfig(); }
        refreshSaveInfo();
        makeMenuWorld();
        setupInput();
        if (!GraphicsEnvironment.isHeadless()) audio.start();
    }

    void makeMenuWorld() {
        World w = new World(menuSeed, 5, false);
        w.fx = 8.5;
        w.fz = 8.5;
        w.loadAround(5);
        menuWorld = w;
        orbitX = 8.5;
        orbitZ = 8.5;
        orbitY = w.heightAt(8, 8) + 20;
        if (state == State.SPLASH || state == State.MENU) world = w;
    }

    void newSeed() {
        menuSeed = 100000 + rnd.nextInt(900000);
        makeMenuWorld();
    }

    int mobSeq;
    double scx, scy, scz;                                     // центр, вокруг которого сейчас появляются мобы
    boolean soundOn = true;
    final Audio audio = new Audio();
    double stepAcc, tickT, splashCd;

    void snd(String name, double dist, double gain) {
        audio.enabled = soundOn;
        double k = volMaster;
        switch (name) {
            case "stepSoft": case "stepHard": case "stepSand": k *= volSteps; break;
            case "door_open": case "door_close": case "chest": k *= volBlocks; break;
            case "zombie": case "pig": case "cow": case "sheep": case "chicken": case "villager": case "zhurt": k *= volMobs; break;
            case "place": case "breakSoft": case "breakStone": case "breakWood": case "tick": case "splash": k *= volBlocks; break;
            case "rain": case "thunder": k *= volWeather; break;
            default:
                if (name.startsWith("step_")) k *= volSteps;
                else if (name.startsWith("dig_") || name.startsWith("put_")) k *= volBlocks;
                break;
        }
        audio.play(name, dist, gain * k);
    }

    void snd(String name) { snd(name, 0, 1); }

    static String breakSound(int id) { return "dig_" + matName(id); }

    /** Материал блока для звуков: grass dirt stone wood sand gravel snow cloth glass metal. */
    static String matName(int id) {
        switch (id) {
            case 1: case 5: case B_CROP: case B_RIPE: case B_CACTUS: return "grass";
            case 2: case 20: return "dirt";
            case 8: return "sand";
            case 9: return "snow";
            case 14: return "gravel";
            case 18: case B_BED: case B_WOOL_R: case B_WOOL_B: return "cloth";
            case B_GLASS: case 19: case B_LAMP: case B_PORTAL: return "glass";
            case B_LANT: case 77: case 78: case 79: case 80: return "metal";
            case 4: case 6: case 21: case B_CHEST: case B_SHELF: case 81: case 82:
            case B_DOOR_L: case B_DOOR_U: case B_DOOR_LO: case B_DOOR_UO: case B_TRAP: case B_TRAP_O: return "wood";
            default: return "stone";
        }
    }

    static String mobSound(int type) {
        switch (type) {
            case MobModels.ZOMBIE: return "zombie";
            case MobModels.PIG: return "pig";
            case MobModels.SHEEP: return "sheep";
            case MobModels.COW: return "cow";
            case MobModels.CHICKEN: return "chicken";
            default: return "villager";
        }
    }


    // ====================================================================
    //  Настройки
    // ====================================================================
    static final class Opt {
        final String key, name;
        final String[] vals;
        final int cat, def;
        int v;
        java.util.function.IntSupplier read;
        java.util.function.IntConsumer write;

        Opt(int cat, String key, String name, int def, String... vals) { this.cat = cat; this.key = key; this.name = name; this.def = def; this.vals = vals; this.v = def; }

        int get() { return read != null ? read.getAsInt() : v; }

        void set(int i) { v = ((i % vals.length) + vals.length) % vals.length; if (write != null) write.accept(v); }
    }

    static final String[] CATS = {"Графика", "Звук", "Управление", "Игра", "Интерфейс", "Оптимизация"};
    boolean hungerOn = true;
    int mapSize = 96;
    double food = 20, starveT;
    int mobDist, animalCap = 9, dropCap = 100, renderEvery = 1, frameNo;
    boolean autoPause, loadingOpts;
    static final int[] CROSS_COLS = {0xFFFFFF, 0xFF4040, 0x50FF50, 0xFFE040, 0x40C0FF};
    final List<Opt> opts = new ArrayList<>();
    final Map<String, Opt> optMap = new HashMap<>();
    double fovTan = Math.tan(Math.toRadians(35)), brightAdd, fogK = 1, sensK = 1, saveEvery = 45, timeK = 1;
    double volMaster = 1, volSteps = 1, volMobs = 1, volBlocks = 1, volWeather = 1;
    boolean fxWeather = true, smoothImg, invY, sprintAlways, wheelInv, keepInv, animalsOn = true, showStatus = true, showHints = true, showFps, showNotes = true, showTools = true;
    int fpsMs = 16, diff = 2, crossStyle, crossCol;

    int zdmg() { return diff == 1 ? 2 : diff == 2 ? 3 : 5; }

    Opt addOpt(Opt o) { opts.add(o); optMap.put(o.key, o); return o; }

    void initOpts() {
        String[] pct = {"0%", "20%", "40%", "60%", "80%", "100%"};
        Opt o;
        o = addOpt(new Opt(0, "view", "Дальность прорисовки (в новой игре)", 1, "Малая (4)", "Средняя (6)", "Большая (8)"));
        o.read = () -> viewR == 4 ? 0 : viewR == 6 ? 1 : 2;
        o.write = i -> viewR = new int[]{4, 6, 8}[i];
        o = addOpt(new Opt(0, "saver", "Экономия ресурсов (в новой игре)", 1, "выкл", "вкл"));
        o.read = () -> saver ? 1 : 0;
        o.write = i -> saver = i == 1;
        addOpt(new Opt(0, "fov", "Поле зрения", 2, "50", "60", "70", "80", "90", "100", "110"));
        addOpt(new Opt(0, "bright", "Яркость", 1, "Тусклая", "Обычная", "Яркая", "Очень яркая"));
        addOpt(new Opt(0, "fog", "Туман вдали", 2, "Нет", "Слабый", "Обычный", "Сильный"));
        addOpt(new Opt(0, "fxweather", "Дождь и снег на экране", 1, "выкл", "вкл"));
        addOpt(new Opt(0, "fpscap", "Лимит кадров", 1, "30", "60", "120", "Без лимита"));
        addOpt(new Opt(0, "smooth", "Сглаживание картинки", 0, "выкл", "вкл"));
        o = addOpt(new Opt(1, "sound", "Звук", 1, "выкл", "вкл"));
        o.read = () -> soundOn ? 1 : 0;
        o.write = i -> soundOn = i == 1;
        addOpt(new Opt(1, "vmaster", "Общая громкость", 5, pct));
        addOpt(new Opt(1, "vsteps", "Громкость шагов", 5, pct));
        addOpt(new Opt(1, "vmobs", "Громкость мобов", 5, pct));
        addOpt(new Opt(1, "vblocks", "Громкость блоков", 5, pct));
        addOpt(new Opt(1, "vweather", "Громкость погоды", 5, pct));
        addOpt(new Opt(2, "sens", "Чувствительность мыши", 3, "25%", "50%", "75%", "100%", "150%", "200%", "300%"));
        addOpt(new Opt(2, "invy", "Инвертировать мышь по Y", 0, "выкл", "вкл"));
        addOpt(new Opt(2, "sprint", "Бег", 0, "по Ctrl", "всегда"));
        addOpt(new Opt(2, "wheel", "Колесо мыши", 0, "обычное", "наоборот"));
        addOpt(new Opt(3, "diff", "Сложность", 2, "Мирно", "Легко", "Нормально", "Сложно"));
        addOpt(new Opt(3, "autosave", "Автосохранение", 2, "15 с", "30 с", "45 с", "90 с", "выкл"));
        addOpt(new Opt(3, "timek", "Скорость времени", 1, "0.5x", "1x", "2x", "4x"));
        addOpt(new Opt(3, "keepinv", "Не терять вещи при смерти", 0, "выкл", "вкл"));
        addOpt(new Opt(3, "animals", "Появление животных", 1, "выкл", "вкл"));
        addOpt(new Opt(3, "hunger", "Голод", 1, "выкл", "вкл"));
        addOpt(new Opt(4, "status", "Строка статуса", 1, "скрыть", "показать"));
        addOpt(new Opt(4, "hints", "Подсказки управления", 1, "скрыть", "показать"));
        addOpt(new Opt(4, "cross", "Прицел", 0, "Крест", "Точка", "Скрыт"));
        addOpt(new Opt(4, "crosscol", "Цвет прицела", 0, "Белый", "Красный", "Зелёный", "Жёлтый", "Голубой"));
        addOpt(new Opt(4, "showfps", "Счётчик FPS", 0, "скрыть", "показать"));
        addOpt(new Opt(4, "notes", "Сообщения на экране", 1, "скрыть", "показать"));
        addOpt(new Opt(4, "tools", "Панель инструментов", 1, "скрыть", "показать"));
        addOpt(new Opt(4, "minimap", "Мини-карта (клавиша M)", 1, "выкл", "малая", "большая"));
        o = addOpt(new Opt(5, "profile", "Профиль производительности", 1, "Максимум", "Баланс", "Быстро", "Минимум"));
        o.write = i -> { if (!loadingOpts) applyProfile(i); };
        addOpt(new Opt(5, "res", "Разрешение рендера", 2, "240x135", "320x180", "480x270", "640x360"));
        addOpt(new Opt(5, "every", "Рендер каждый N-й кадр", 0, "1 (плавно)", "2", "3"));
        addOpt(new Opt(5, "threads", "Потоки рендера", 0, "Авто", "1", "2", "4"));
        addOpt(new Opt(5, "mobdist", "Дальность прорисовки мобов", 3, "16", "32", "64", "Без ограничения"));
        addOpt(new Opt(5, "lightn", "Скорость пересчёта света", 2, "1 (медленно)", "2", "4", "8 (быстро)"));
        addOpt(new Opt(5, "animalcap", "Лимит животных", 2, "3", "6", "9", "15"));
        addOpt(new Opt(5, "dropcap", "Лимит предметов на земле", 2, "20", "50", "100", "300"));
        addOpt(new Opt(5, "autopause", "Пауза при потере фокуса", 0, "выкл", "вкл"));
        applyOpts();
    }

    void applyProfile(int p) {
        // res, every, view(0..2), fog, fxweather, smooth, mobdist, animalcap, dropcap
        int[][] t = {{3, 0, 2, 2, 1, 1, 3, 3, 3}, {2, 0, 1, 2, 1, 0, 3, 2, 2}, {1, 0, 0, 1, 0, 0, 1, 1, 1}, {0, 1, 0, 0, 0, 0, 0, 0, 0}};
        int[] v = t[p];
        String[] k = {"res", "every", "view", "fog", "fxweather", "smooth", "mobdist", "animalcap", "dropcap"};
        for (int i = 0; i < k.length; i++) optMap.get(k[i]).set(v[i]);
        applyOpts();
    }

    int ov(String k) { return optMap.get(k).get(); }

    void applyOpts() {
        fovTan = Math.tan(Math.toRadians((50 + 10 * ov("fov")) / 2.0));
        brightAdd = new double[]{-0.07, 0, 0.09, 0.18}[ov("bright")];
        fogK = new double[]{0, 0.5, 1, 1.7}[ov("fog")];
        fxWeather = ov("fxweather") == 1;
        fpsMs = new int[]{33, 16, 8, 1}[ov("fpscap")];
        smoothImg = ov("smooth") == 1;
        volMaster = ov("vmaster") / 5.0;
        volSteps = ov("vsteps") / 5.0;
        volMobs = ov("vmobs") / 5.0;
        volBlocks = ov("vblocks") / 5.0;
        volWeather = ov("vweather") / 5.0;
        sensK = new double[]{0.25, 0.5, 0.75, 1, 1.5, 2, 3}[ov("sens")];
        invY = ov("invy") == 1;
        sprintAlways = ov("sprint") == 1;
        wheelInv = ov("wheel") == 1;
        diff = ov("diff");
        saveEvery = new double[]{15, 30, 45, 90, 1e9}[ov("autosave")];
        timeK = new double[]{0.5, 1, 2, 4}[ov("timek")];
        keepInv = ov("keepinv") == 1;
        animalsOn = ov("animals") == 1;
        showStatus = ov("status") == 1;
        showHints = ov("hints") == 1;
        crossStyle = ov("cross");
        crossCol = ov("crosscol");
        showFps = ov("showfps") == 1;
        showNotes = ov("notes") == 1;
        showTools = ov("tools") == 1;
        hungerOn = ov("hunger") == 1;
        mapSize = new int[]{0, 96, 144}[ov("minimap")];
        int[][] rs = {{240, 135}, {320, 180}, {480, 270}, {640, 360}};
        setRes(rs[ov("res")][0], rs[ov("res")][1]);
        renderEvery = ov("every") + 1;
        int th = new int[]{0, 1, 2, 4}[ov("threads")];
        if (th == 0) rpool = null;
        else if (rpool == null || rpool.getParallelism() != th) rpool = new java.util.concurrent.ForkJoinPool(th);
        mobDist = new int[]{16, 32, 64, 0}[ov("mobdist")];
        World.RELIGHT_N = new int[]{1, 2, 4, 8}[ov("lightn")];
        animalCap = new int[]{3, 6, 9, 15}[ov("animalcap")];
        dropCap = new int[]{20, 50, 100, 300}[ov("dropcap")];
        autoPause = ov("autopause") == 1;
    }

    void resetOpts() {
        loadingOpts = true;
        for (Opt o : opts) o.set(o.def);
        loadingOpts = false;
        applyOpts();
        saveConfig();
    }

    /** Кнопки страниц настроек (4 - разделы, 5..9 - параметры раздела). */
    void settingsButtons(List<Btn> l, int w, int y0) {
        if (menuPage == 4) {
            String[] t = new String[CATS.length + 2];
            Runnable[] a = new Runnable[t.length];
            for (int i = 0; i < CATS.length; i++) { final int pg = 5 + i; t[i] = CATS[i]; a[i] = () -> menuPage = pg; }
            t[CATS.length] = "Сбросить все настройки"; a[CATS.length] = this::resetOpts;
            t[CATS.length + 1] = "Готово"; a[CATS.length + 1] = () -> menuPage = 0;
            addBtns(l, w, y0, t, a);
            return;
        }
        List<Opt> co = new ArrayList<>();
        for (Opt o : opts) if (o.cat == menuPage - 5) co.add(o);
        String[] t = new String[co.size() + 1];
        Runnable[] a = new Runnable[t.length];
        Runnable[] r = new Runnable[t.length];
        for (int i = 0; i < co.size(); i++) {
            final Opt o = co.get(i);
            t[i] = o.name + ": " + o.vals[o.get()];
            a[i] = () -> { o.set(o.get() + 1); applyOpts(); saveConfig(); };
            r[i] = () -> { o.set(o.get() - 1); applyOpts(); saveConfig(); };
        }
        t[co.size()] = "Назад"; a[co.size()] = () -> menuPage = 4;
        int n0 = l.size();
        addBtns(l, w, y0, t, a);
        for (int i = 0; i < r.length; i++) l.get(n0 + i).r = r[i];
    }

    void paintSettingsTitle(Graphics2D g, int w, int y0) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        centerText(g, menuPage == 4 ? "Настройки" : "Настройки — " + CATS[menuPage - 5], w / 2, y0 - 28, Color.WHITE);
        if (menuPage >= 5) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            centerText(g, "ЛКМ — следующее значение, ПКМ — предыдущее", w / 2, y0 - 10, new Color(0xDDDDDD));
        }
    }

    void cycleView() { viewR = viewR == 4 ? 6 : viewR == 6 ? 8 : 4; saveConfig(); }

    /** Новая игра. Если на диске уже есть сохранение, оно сохраняется как резервное. */
    void startGame(boolean surv) {
        slot = freeSlot();
        hasSave = false;
        beginSession(menuSeed, surv, null);
    }

    void loadGame() {
        SaveData sd = readSave(false);
        if (sd == null) { hasSave = false; return; }
        beginSession(sd.seed, sd.survival, sd);
    }

    void beginSession(int seed, boolean surv, SaveData sd) {
        survival = surv;
        if (gameWorld != null) gameWorld.stop();
        World nw = new World(seed, viewR, saver);
        if (sd != null && sd.oldGen) nw.biomes = false;
        if (sd != null) {
            for (Chunk c : sd.chunks) { c.modified = true; nw.saved.put(World.key(c.cx, c.cz), c); }
            for (int[] a : sd.tiles) {
                Tile t = new Tile(a[3]);
                t.x = a[0]; t.y = a[1]; t.z = a[2];
                t.burn = a[5] / 100.0; t.burnMax = a[6] / 100.0; t.prog = a[7] / 100.0;
                for (int i = 0; i < t.item.length && 9 + i * 2 < a.length; i++) { t.item[i] = a[8 + i * 2]; t.cnt[i] = a[9 + i * 2]; }
                nw.tiles.put(tkey(t.x, t.y, t.z), t);
            }
        }
        hasBed = sd != null && sd.hasBed;
        if (hasBed) { bedX = sd.bx; bedY = sd.by; bedZ = sd.bz; }
        // ищем место для спауна на нормальной поверхности (не на дереве)
        int sx = 8, sz = 8;
        search:
        for (int pass = 0; pass < 2; pass++)
        for (int r = 0; r < 40; r += 2) {
            for (int a = 0; a < 8; a++) {
                int x = 8 + (int) Math.round(Math.cos(a * Math.PI / 4) * r), z = 8 + (int) Math.round(Math.sin(a * Math.PI / 4) * r);
                int h = nw.heightAt(x, z);
                if (h > SAND_H && h < SNOW_H && (pass == 1 || nw.biome(x, z) == 0) && hash2(x, z, nw.seed + 991) >= 0.02) { sx = x; sz = z; break search; }
            }
        }
        spawnX = sx + 0.5;
        spawnZ = sz + 0.5;
        spawnY = nw.heightAt(sx, sz) + 1;
        if (sd != null) { spawnX = sd.spx; spawnY = sd.spy; spawnZ = sd.spz; }
        nw.fx = sd != null ? sd.px : spawnX;
        nw.fz = sd != null ? sd.pz : spawnZ;
        nw.loadAround(3);
        nw.start();
        gameWorld = nw;
        world = nw;
        pl.x = spawnX; pl.y = spawnY; pl.z = spawnZ;
        pl.vx = pl.vy = pl.vz = 0;
        yaw = 0; pitch = 0; kbx = kbz = 0;
        hp = MAXHP;
        Arrays.fill(inv, 0);
        pick = axe = sword = 0;
        panel = 0;
        air = 10;
        food = 20;
        mobs.clear();
        drops.clear();
        scx = pl.x; scy = pl.y; scz = pl.z;
        initHotbar();
        tod = 0.35;
        fly = false;
        lmb = false;
        mining = false;
        spawnT = 5;
        for (int i = 0; i < 30 && countPassive() < 7; i++) trySpawnAnimal(8, 40);
        msg = "";
        if (sd != null) {
            pl.x = sd.px; pl.y = sd.py; pl.z = sd.pz;
            yaw = sd.yaw; pitch = sd.pitch;
            hp = Math.max(1, sd.hp);
            System.arraycopy(sd.inv, 0, inv, 0, Math.min(inv.length, sd.inv.length));
            pick = sd.pick; axe = sd.axe; sword = sd.sword;
            if (sd.hasHot) {
                System.arraycopy(sd.hot, 0, hot, 0, 9);
                sel = Math.max(0, Math.min(8, sd.sel));
            } else if (survival) {                              // старое сохранение: собираем панель из инвентаря
                for (int id : PLACE_SURV) if (inv[id] > 0) autoHot(id);
                if (inv[I_MEAT] > 0) autoHot(I_MEAT);
            }
            tod = sd.tod;
            fly = sd.fly && !survival;
            msg = "Мир загружен";
            msgT = 2;
        }
        saveT = 0;
        Arrays.fill(keys, false);
        state = State.PLAYING;
        capture();
    }

    void toMenu() {
        if (gameWorld != null) saveSync();
        stopNet();
        menuPage = 0;
        refreshSaveInfo();
        if (gameWorld != null) gameWorld.stop();
        gameWorld = null;
        mobs.clear();
        drops.clear();
        world = menuWorld;
        state = State.MENU;
        lmb = false;
        release();
    }

    void pause() {
        if (panel == 1 || panel == 3) panel = 0;
        state = State.PAUSED;
        menuPage = 0;
        lmb = false;
        Arrays.fill(keys, false);
        release();
    }

    void resume() {
        state = State.PLAYING;
        capture();
    }

    void respawn() {
        double rx = spawnX, ry = spawnY, rz = spawnZ;
        if (hasBed) {
            int b = world.get(bedX, bedY, bedZ);
            if (b == B_BED || b < 0) { rx = bedX + 0.5; ry = bedY + 0.05; rz = bedZ + 0.5; }
            else { hasBed = false; msg = "Ваша кровать разрушена"; msgT = 3; }
        }
        protT = 3;
        food = 20;
        pl.x = rx; pl.y = ry; pl.z = rz;
        pl.vx = pl.vy = pl.vz = 0;
        kbx = kbz = 0;
        world.fx = rx;
        world.fz = rz;
        world.loadAround(2);
        hp = MAXHP;
        if (!isNet()) mobs.clear();
        state = State.PLAYING;
        capture();
    }

    // ====================================================================
    //  Сетевая игра: хост (сервер внутри игры)
    // ====================================================================
    /** Другой игрок, которого мы видим. Позиция сглаживается к целевой. */
    static final class RPlayer {
        int id;
        String name = "";
        double x, y, z, yaw, tx, ty, tz, tyaw, phase, swing, pitch, tpitch;
        boolean creator;
    }

    String myUid = "";                                        // постоянный номер этого игрока (по нему хост узнаёт вернувшегося друга)
    boolean hasBed;                                           // есть ли кровать (точка возрождения)
    int bedX, bedY, bedZ;

    /** Всё, что хост помнит о друге: вещи, панель, инструменты, здоровье, место и кровать. */
    static final class PData {
        String uid = "", name = "";
        double x, y, z;
        int hp = MAXHP, sel, pick, axe, sword, bx, by, bz;
        boolean hasBed;
        final int[] inv = new int[NITEM];
        final int[] hot = new int[9];
    }

    final Map<String, PData> pdata = new java.util.concurrent.ConcurrentHashMap<>();
    int lastInvHash;
    double invSyncT;

    static File playersFile() { return new File(worldsDir(), slot + ".players"); }

    void writePlayers() {
        if (gameWorld == null) return;
        File f = playersFile(), tmp = new File(f.getParentFile(), "players.tmp");
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp))))) {
            o.writeInt(0x4D435051);
            o.writeInt(gameWorld.seed);
            o.writeInt(pdata.size());
            for (PData d : pdata.values()) {
                o.writeUTF(d.uid); o.writeUTF(d.name);
                o.writeDouble(d.x); o.writeDouble(d.y); o.writeDouble(d.z);
                o.writeInt(d.hp); o.writeInt(d.sel); o.writeInt(d.pick); o.writeInt(d.axe); o.writeInt(d.sword);
                o.writeBoolean(d.hasBed); o.writeInt(d.bx); o.writeInt(d.by); o.writeInt(d.bz);
                o.writeInt(d.inv.length);
                for (int v : d.inv) o.writeInt(v);
                for (int v : d.hot) o.writeInt(v);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try { Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING); } catch (IOException e2) { e2.printStackTrace(); }
        }
    }

    /** Читает вещи друзей этого мира (если файл от другого мира, то игнорирует). */
    void readPlayers() {
        pdata.clear();
        File f = playersFile();
        if (!f.exists() || gameWorld == null) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f))))) {
            if (in.readInt() != 0x4D435051 || in.readInt() != gameWorld.seed) return;
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                PData d = new PData();
                d.uid = in.readUTF(); d.name = in.readUTF();
                d.x = in.readDouble(); d.y = in.readDouble(); d.z = in.readDouble();
                d.hp = in.readInt(); d.sel = in.readInt(); d.pick = in.readInt(); d.axe = in.readInt(); d.sword = in.readInt();
                d.hasBed = in.readBoolean(); d.bx = in.readInt(); d.by = in.readInt(); d.bz = in.readInt();
                int k = in.readInt();
                for (int j = 0; j < k; j++) { int v = in.readInt(); if (j < NITEM) d.inv[j] = v; }
                for (int j = 0; j < 9; j++) d.hot[j] = in.readInt();
                pdata.put(d.uid, d);
            }
        } catch (Exception ignored) { }
    }

    void savePlayers() {
        if (server != null || !pdata.isEmpty()) writePlayers();
    }

    int invHash() {
        int h = hp * 31 + sel * 7 + pick * 131 + axe * 17 + sword * 3 + (hasBed ? bedX * 5 + bedY * 11 + bedZ * 13 : 0);
        for (int i = 0; i < NITEM; i++) h = h * 31 + inv[i];
        for (int v : hot) h = h * 31 + v;
        return h;
    }

    /** Пакет со всем состоянием игрока: клиент -> хост (C_INV) или хост -> клиент (S_INV). */
    static byte[] invPacket(byte type, PData d) {
        Net.W w = new Net.W(type).i(d.hp).i(d.sel).i(d.pick).i(d.axe).i(d.sword).b(d.hasBed ? 1 : 0).i(d.bx).i(d.by).i(d.bz);
        for (int i = 0; i < NITEM; i++) w.i(d.inv[i]);
        for (int i = 0; i < 9; i++) w.i(d.hot[i]);
        return w.done();
    }

    PData myData() {
        PData d = new PData();
        d.hp = hp; d.sel = sel; d.pick = pick; d.axe = axe; d.sword = sword;
        d.hasBed = hasBed; d.bx = bedX; d.by = bedY; d.bz = bedZ;
        System.arraycopy(inv, 0, d.inv, 0, NITEM);
        System.arraycopy(hot, 0, d.hot, 0, 9);
        return d;
    }

    /** Читает состояние игрока из пакета в PData. */
    static void readInvInto(DataInputStream in, PData d) throws IOException {
        d.hp = in.readInt(); d.sel = in.readInt(); d.pick = in.readInt(); d.axe = in.readInt(); d.sword = in.readInt();
        d.hasBed = in.readByte() != 0; d.bx = in.readInt(); d.by = in.readInt(); d.bz = in.readInt();
        for (int i = 0; i < NITEM; i++) d.inv[i] = in.readInt();
        for (int i = 0; i < 9; i++) d.hot[i] = in.readInt();
    }

    void applyData(PData d) {
        hp = d.hp <= 0 ? MAXHP : Math.min(MAXHP, d.hp);
        sel = Math.max(0, Math.min(8, d.sel));
        pick = d.pick; axe = d.axe; sword = d.sword;
        hasBed = d.hasBed; bedX = d.bx; bedY = d.by; bedZ = d.bz;
        System.arraycopy(d.inv, 0, inv, 0, NITEM);
        System.arraycopy(d.hot, 0, hot, 0, 9);
        lastInvHash = invHash();
    }

    Net.Server server;                                        // не null, если мы хост
    Net.Conn clientConn;                                      // не null, если мы подключены к чужому хосту
    final Map<Integer, RPlayer> remotes = new HashMap<>();
    int myId;
    String myName = cleanName(System.getProperty("user.name", "Player"), 0);
    double netT, netTimeT;
    String joinStatus = "", joinText = "", lastAddr = "";
    int menuPage, field;                                      // страница меню (0 главная, 1 сеть, 2 подключение), активное поле ввода
    boolean joining;
    final List<String> chatLog = new ArrayList<>();
    final List<Double> chatTime = new ArrayList<>();
    boolean chatOpen;
    double chatOpenT;
    String chatText = "";

    boolean isHost() { return server != null; }

    boolean isClient() { return clientConn != null; }

    boolean isNet() { return server != null || clientConn != null; }

    void chatAdd(String s) {
        chatLog.add(s);
        chatTime.add(animT);
        while (chatLog.size() > 60) { chatLog.remove(0); chatTime.remove(0); }
    }

    final Net.Handler serverHandler = new Net.Handler() {
        public void onPacket(Net.Conn c, byte[] d) { synchronized (lock) { hostPacket(c, d); } }

        public void onClosed(Net.Conn c, String why) {
            synchronized (lock) {
                remotes.remove(c.id); skins.remove(c.id); skinPngs.remove(c.id);
                if (c.ready) {
                    c.ready = false;
                    PData pd = pdata.get(c.uid);
                    if (pd != null) { pd.x = c.x; pd.y = c.y; pd.z = c.z; writePlayers(); }
                    String m = c.name + " вышел из игры";
                    chatAdd(m);
                    if (server != null) server.broadcast(new Net.W(Net.S_CHAT).s(m).done(), null);
                }
            }
        }
    };

    /** Открывает текущий мир для подключений по сети. */
    boolean startHosting() {
        if (server != null || clientConn != null || gameWorld == null) return false;
        Net.Server s = new Net.Server();
        try {
            s.start(Net.PORT, serverHandler);
        } catch (IOException e) {
            msg = "Не удалось открыть порт " + Net.PORT + ": " + e.getMessage();
            msgT = 4;
            return false;
        }
        server = s;
        readPlayers();
        chatAdd("Мир открыт для сети. Порт " + Net.PORT);
        List<String> ips = Net.localAddresses();
        msg = "Мир открыт. Друзья: " + (ips.isEmpty() ? "127.0.0.1" : ips.get(0)) + ":" + Net.PORT + " (все адреса — в паузе)";
        msgT = 6;
        return true;
    }

    void stopNet() {
        if (server != null) { server.stop(); server = null; }
        if (clientConn != null) {
            final Net.Conn cc = clientConn;
            clientConn = null;
            if (gameWorld != null) cc.send(invPacket(Net.C_INV, myData()));      // последний снимок вещей
            Thread t = new Thread(() -> {
                try { Thread.sleep(400); } catch (InterruptedException ignored) { }
                cc.close();
            });
            t.setDaemon(true);
            t.start();
        }
        remotes.clear(); skins.clear(); skinPngs.clear(); skinSent = false;
        if (gameWorld != null) gameWorld.extra = new double[0][];
        chatOpen = false;
    }

    /** Ник: только английские буквы, цифры и _ (без пробелов). */
    static boolean nickChar(char ch) { return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_'; }

    static String filterNick(String c) {
        StringBuilder sb = new StringBuilder();
        for (char ch : c.toCharArray()) if (nickChar(ch) && sb.length() < 16) sb.append(ch);
        return sb.toString();
    }

    static String cleanName(String n, int id) {
        StringBuilder sb = new StringBuilder();
        for (char ch : n.toCharArray()) if (nickChar(ch) && sb.length() < 16) sb.append(ch);
        String s = sb.toString();
        return s.isEmpty() ? "Player" + id : s;
    }

    void kick(Net.Conn c, String why) {
        c.send(new Net.W(Net.S_KICK).s(why).done());
        Thread t = new Thread(() -> {
            try { Thread.sleep(400); } catch (InterruptedException ignored) { }
            c.close();
        });
        t.setDaemon(true);
        t.start();
    }

    void hostPacket(Net.Conn c, byte[] d) {
        if (server == null) return;                           // хост уже закрыт, запоздавший пакет игнорируем
        try {
            DataInputStream in = Net.reader(d);
            switch (d[0]) {
                case Net.C_HELLO: {
                    int ver = in.readInt();
                    if (ver != Net.VERSION) { kick(c, "Другая версия игры (у хоста " + Net.VERSION + ", у вас " + ver + ")"); return; }
                    String name = in.readUTF();
                    String uid = in.readUTF();
                    String proof = in.readUTF();
                    if (uid.length() < 8 || uid.length() > 64) { kick(c, "Ошибка идентификатора игрока"); return; }
                    if (gameWorld == null || c.ready) return;
                    for (Net.Conn o : server.conns) if (o != c && o.ready && o.uid.equals(uid)) { kick(c, "Этот игрок уже в игре"); return; }
                    c.name = cleanName(name, c.id);
                    String lerr = verifyLogin(c.name, proof);
                    if (lerr != null) { kick(c, lerr); return; }
                    c.creator = c.name.equalsIgnoreCase(CREATOR_NICK);
                    c.uid = uid;
                    PData pd = pdata.get(uid);
                    sendWelcome(c, pd);
                    if (pd != null) {
                        pd.name = c.name;
                        c.x = pd.x; c.y = pd.y; c.z = pd.z;
                        c.send(invPacket(Net.S_INV, pd));                                 // возвращаем вещи
                    } else {
                        c.x = pl.x; c.y = pl.y; c.z = pl.z;
                        pd = new PData();
                        pd.uid = uid; pd.name = c.name; pd.x = c.x; pd.y = c.y; pd.z = c.z;
                        pdata.put(uid, pd);
                    }
                    c.ready = true;
                    RPlayer r = new RPlayer();
                    r.id = c.id; r.name = c.name; r.x = r.tx = c.x; r.y = r.ty = c.y; r.z = r.tz = c.z; r.creator = c.creator;
                    remotes.put(c.id, r);
                    if (mySkinPng != null) skinPngs.put(0, mySkinPng);
                    for (Map.Entry<Integer, byte[]> se : skinPngs.entrySet())
                        c.send(new Net.W(Net.S_SKIN).i(se.getKey()).i(se.getValue().length).raw(se.getValue()).done());
                    String m = tagName(c.name, c.creator) + " подключился";
                    chatAdd(m);
                    server.broadcast(new Net.W(Net.S_CHAT).s(m).done(), null);
                    break;
                }
                case Net.C_STATE: {
                    if (!c.ready) return;
                    c.x = in.readFloat(); c.y = in.readFloat(); c.z = in.readFloat();
                    c.yaw = in.readFloat(); c.pitch = in.readFloat();
                    RPlayer r = remotes.get(c.id);
                    if (r != null) { r.tx = c.x; r.ty = c.y; r.tz = c.z; r.tyaw = c.yaw; r.tpitch = c.pitch; }
                    break;
                }
                case Net.C_BLOCK: {
                    if (!c.ready) return;
                    int x = in.readInt(), y = in.readInt(), z = in.readInt(), id = in.readInt();
                    if (y < 1 || y >= WY || id < 0 || id >= NITEM) return;
                    if (Math.abs(x - c.x) > 14 || Math.abs(z - c.z) > 14 || Math.abs(y - c.y) > 20) return;   // слишком далеко от игрока
                    int old = gameWorld.get(x, y, z);
                    if (isTileBlock(old) && id != old) breakTile(x, y, z);
                    gameWorld.edit(x, y, z, id);
                    server.broadcast(new Net.W(Net.S_BLOCK).i(x).i(y).i(z).i(id).done(), c);
                    break;
                }
                case Net.C_ATTACK: {
                    if (!c.ready) return;
                    int mid = in.readInt();
                    int dmg = Math.max(0, Math.min(100, in.readInt()));
                    for (Mob m : mobs) {
                        if (m.id == mid && Math.hypot(m.x - c.x, m.z - c.z) < 7) { hitMob(m, dmg, c.x, c.z, c); break; }
                    }
                    break;
                }
                case Net.C_HIT: {
                    if (!c.ready) return;
                    int vid = in.readInt();
                    int dmg = Math.max(0, Math.min(20, in.readInt()));
                    boolean near = vid == 0 ? Math.hypot(pl.x - c.x, pl.z - c.z) < 6 : false;
                    for (Net.Conn o : server.conns) if (o.ready && o.id == vid && o != c && Math.hypot(o.x - c.x, o.z - c.z) < 6) near = true;
                    if (near) hitPlayer(vid, dmg, c.x, c.z);
                    break;
                }
                case Net.C_SKIN: {
                    if (!c.ready) return;
                    int len = in.readInt();
                    if (len <= 0 || len > 30000) return;
                    byte[] b = new byte[len];
                    in.readFully(b);
                    int[] sk = decodeSkin(b);
                    if (sk == null) return;
                    skins.put(c.id, sk);
                    skinPngs.put(c.id, b);
                    server.broadcast(new Net.W(Net.S_SKIN).i(c.id).i(len).raw(b).done(), c);
                    break;
                }
                case Net.C_SLEEP: {
                    if (!c.ready) return;
                    c.sleeping = in.readByte() != 0 && daylight < 0.4;
                    break;
                }
                case Net.C_SPILL: {
                    if (!c.ready) return;
                    int n = Math.min(64, in.readInt());
                    for (int i = 0; i < n; i++) {
                        int item = in.readInt(), cnt = in.readInt();
                        if (item <= 0 || item >= NITEM || cnt <= 0 || cnt > 5000) continue;
                        spawnDrop(item, cnt, c.x, c.y + 0.6, c.z, rnd.nextDouble() * 4 - 2, 4 + rnd.nextDouble() * 2, rnd.nextDouble() * 4 - 2);
                    }
                    announce(c.name + " погиб");
                    break;
                }
                case Net.C_TILE: {
                    if (!c.ready) return;
                    int op = in.readByte();
                    int x = in.readInt(), y = in.readInt(), z = in.readInt(), slot = in.readInt(), item = in.readInt(), cnt = in.readInt();
                    if (Math.abs(x + 0.5 - c.x) > 8 || Math.abs(z + 0.5 - c.z) > 8 || Math.abs(y - c.y) > 8) {
                        if (op == 1 && item > 0 && cnt > 0) c.send(new Net.W(Net.S_LOOT).i(1).i(item).i(cnt).done());   // вернуть вещи
                        return;
                    }
                    Tile t = gameWorld.getTile(x, y, z);
                    if (t == null) {
                        if (op == 1 && item > 0 && cnt > 0) c.send(new Net.W(Net.S_LOOT).i(1).i(item).i(cnt).done());
                        return;
                    }
                    if (op == 1) {
                        if (cnt < 1 || cnt > 2000 || !tilePutAt(t, slot, item, cnt)) c.send(new Net.W(Net.S_LOOT).i(1).i(item).i(Math.max(1, Math.min(cnt, 2000))).done());
                    } else if (op == 2 && slot >= 0 && slot < t.item.length && t.item[slot] > 0) {
                        c.send(new Net.W(Net.S_LOOT).i(1).i(t.item[slot]).i(t.cnt[slot]).done());
                        t.item[slot] = 0;
                        t.cnt[slot] = 0;
                    }
                    c.send(tilePacket(t));
                    break;
                }
                case Net.C_INV: {
                    PData pd = pdata.get(c.uid);
                    if (pd == null || c.uid.isEmpty()) return;
                    readInvInto(in, pd);
                    pd.name = c.name;
                    pd.x = c.x; pd.y = c.y; pd.z = c.z;
                    break;
                }
                case Net.C_DROP: {
                    if (!c.ready) return;
                    int item = in.readInt(), cnt = in.readInt();
                    if (item <= 0 || item >= NITEM || cnt <= 0 || cnt > 2000) return;
                    double cp = Math.cos(c.pitch);
                    double dx = cp * Math.sin(c.yaw), dy = Math.sin(c.pitch), dz = cp * Math.cos(c.yaw);
                    spawnDrop(item, cnt, c.x + dx * 0.5, c.y + 1.4, c.z + dz * 0.5, dx * 5, dy * 5 + 2.5, dz * 5);
                    break;
                }
                case Net.C_CHAT: {
                    if (!c.ready) return;
                    String t = in.readUTF().trim();
                    if (t.isEmpty()) return;
                    String line = tagName(c.name, c.creator) + ": " + t;
                    chatAdd(line);
                    server.broadcast(new Net.W(Net.S_CHAT).s(line).done(), null);
                    break;
                }
                default:
                    break;
            }
        } catch (IOException e) {
            c.close();
        }
    }

    void sendWelcome(Net.Conn c, PData pd) throws IOException {
        double sx = pd != null ? pd.x : pl.x, sy = pd != null ? pd.y : pl.y, sz = pd != null ? pd.z : pl.z;
        Net.W w = new Net.W(Net.S_WELCOME).i(Net.VERSION).i(c.id).i(gameWorld.seed).b(survival ? 1 : 0).f(tod)
                .f(sx).f(sy).f(sz).s(myName).f(spawnX).f(spawnY).f(spawnZ);
        List<Chunk> mod = gameWorld.modifiedChunks();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream gz = new DataOutputStream(new GZIPOutputStream(bo))) {
            gz.writeInt(mod.size());
            for (Chunk ch : mod) { gz.writeInt(ch.cx); gz.writeInt(ch.cz); gz.write(ch.b); }
        }
        byte[] z = bo.toByteArray();
        w.i(z.length).raw(z);
        c.send(w.done());
    }

    void sendHurt(Net.Conn c, int dmg, double kx, double kz) {
        c.send(new Net.W(Net.S_HURT).i(dmg).f(kx).f(kz).done());
    }

    /** Центры загрузки чанков и рассылка состояния мира подключённым игрокам (20 раз в секунду). */
    void hostTick(double dt) {
        List<double[]> anchors = new ArrayList<>();
        for (Net.Conn c : server.conns) if (c.ready) anchors.add(new double[]{c.x, c.z, Math.sin(c.yaw), Math.cos(c.yaw)});
        gameWorld.extra = anchors.toArray(new double[0][]);
        netT += dt;
        if (netT < 0.05) return;
        netT = 0;
        Net.W p = new Net.W(Net.S_PLAYERS);
        List<Net.Conn> rc = new ArrayList<>();
        for (Net.Conn c : server.conns) if (c.ready) rc.add(c);
        p.i(1 + rc.size()).i(0).s(myName).f(pl.x).f(pl.y).f(pl.z).f(yaw).f(pitch).b(selfCreator() ? 1 : 0);
        for (Net.Conn c : rc) p.i(c.id).s(c.name).f(c.x).f(c.y).f(c.z).f(c.yaw).f(c.pitch).b(c.creator ? 1 : 0);
        server.broadcastLossy(p.done());
        Net.W m = new Net.W(Net.S_MOBS).i(mobs.size());
        for (Mob mob : mobs) {
            m.i(mob.id).b(mob.type).f(mob.x).f(mob.y).f(mob.z).f(mob.ang).f(mob.phase)
                    .b((int) Math.round(clamp01(mob.swing) * 100)).b(mob.hurt > 0 ? 1 : 0);
        }
        server.broadcastLossy(m.done());
        if (!drops.isEmpty() || sentDrops > 0) {
            sentDrops = drops.size();
            Net.W dw = new Net.W(Net.S_DROPS).i(drops.size());
            for (Drop d : drops) dw.i(d.id).i(d.item).i(d.cnt).f(d.x).f(d.y).f(d.z);
            server.broadcastLossy(dw.done());
        }
        netTimeT += 0.05;
        if (netTimeT > 2) { netTimeT = 0; server.broadcast(new Net.W(Net.S_TIME).f(tod).done(), null); sendFlags(); }
        if (sleepN > 0 || sentSleep) {
            sentSleep = sleepN > 0;
            server.broadcastLossy(new Net.W(Net.S_SLEEP).i(sleepN).i(sleepTotal).done());
        }
    }

    /** Игроки, которых видят мобы: хост и все подключённые. */
    static final class Target {
        double x, y, z, d;
        Net.Conn conn;                                        // null - это хост
    }

    Target nearestPlayer(double x, double z) {
        Target t = new Target();
        t.x = pl.x; t.y = pl.y; t.z = pl.z;
        t.d = Math.hypot(pl.x - x, pl.z - z);
        if (server != null) {
            for (Net.Conn c : server.conns) {
                if (!c.ready) continue;
                double d = Math.hypot(c.x - x, c.z - z);
                if (d < t.d) { t.x = c.x; t.y = c.y; t.z = c.z; t.d = d; t.conn = c; }
            }
        }
        return t;
    }


    // ====================================================================
    //  Сетевая игра: клиент (подключение к чужому миру)
    // ====================================================================
    String kickReason, menuNote = "";
    double joinStart;

    final Net.Handler clientHandler = new Net.Handler() {
        public void onPacket(Net.Conn c, byte[] d) {
            synchronized (lock) { if (c == clientConn) clientPacket(d); }
        }

        public void onClosed(Net.Conn c, String why) {
            synchronized (lock) {
                if (c != clientConn) return;
                clientConn = null;
                joining = false;
                String m = kickReason != null ? kickReason : "Соединение с хостом потеряно";
                kickReason = null;
                if (gameWorld != null && state != State.MENU && state != State.SPLASH) leaveClient(m);
                else joinStatus = m;
            }
        }
    };

    /** Выход из чужого мира в меню (после потери связи). */
    void leaveClient(String note) {
        if (gameWorld != null) gameWorld.stop();
        gameWorld = null;
        mobs.clear();
        drops.clear();
        remotes.clear(); skins.clear(); skinPngs.clear(); skinSent = false;
        world = menuWorld;
        state = State.MENU;
        menuPage = 0;
        menuNote = note;
        lmb = false;
        chatOpen = false;
        release();
    }

    /** Подключение к хосту. addr - "адрес" или "адрес:порт". */
    void connectTo(String addr) {
        if (joining || isNet()) return;
        String host = addr.trim();
        int port = Net.PORT;
        int ci = host.lastIndexOf(':');
        if (ci > 0 && host.indexOf(':') == ci) {
            try { port = Integer.parseInt(host.substring(ci + 1).trim()); } catch (NumberFormatException e) { joinStatus = "Неверный порт"; return; }
            host = host.substring(0, ci).trim();
        }
        if (host.isEmpty()) { joinStatus = "Введите адрес хоста"; return; }
        myName = cleanName(myName, 0);
        saveConfig();
        joining = true;
        joinStart = animT;
        joinStatus = "Подключаюсь к " + host + ":" + port + " ...";
        final String h = host;
        final int pt = port;
        Thread t = new Thread(() -> {
            try {
                Net.Conn c = Net.connect(h, pt, 6000);
                synchronized (lock) {
                    if (!joining) { c.close(); return; }
                    clientConn = c;
                    kickReason = null;
                }
                Net.run(c, clientHandler);
                c.send(new Net.W(Net.C_HELLO).i(Net.VERSION).s(myName).s(myUid).s(proofNick.equalsIgnoreCase(myName) ? acctProof : "").done());
                synchronized (lock) { if (joining) joinStatus = "Жду ответа хоста ..."; }
            } catch (Exception e) {
                String m = e instanceof java.net.UnknownHostException ? "Адрес не найден: " + h
                        : e instanceof java.net.SocketTimeoutException ? "Хост не отвечает (время вышло)"
                        : e instanceof java.net.ConnectException ? "Соединение отклонено: мир не открыт или порт закрыт"
                        : "Не удалось подключиться: " + e.getMessage();
                synchronized (lock) { joining = false; joinStatus = m; }
            }
        }, "net-connect");
        t.setDaemon(true);
        t.start();
    }

    void cancelJoin() {
        joining = false;
        if (clientConn != null && gameWorld == null) { Net.Conn c = clientConn; clientConn = null; c.close(); }
        joinStatus = "";
    }

    void clientPacket(byte[] d) {
        try {
            DataInputStream in = Net.reader(d);
            switch (d[0]) {
                case Net.S_WELCOME: {
                    int ver = in.readInt();
                    int id = in.readInt();
                    int seed = in.readInt();
                    boolean surv = in.readByte() != 0;
                    double todv = in.readFloat();
                    double x = in.readFloat(), y = in.readFloat(), z = in.readFloat();
                    String hostName = in.readUTF();
                    double rsx = in.readFloat(), rsy = in.readFloat(), rsz = in.readFloat();
                    int zl = in.readInt();
                    byte[] zb = new byte[zl];
                    in.readFully(zb);
                    if (ver != Net.VERSION) { kickReason = "Другая версия игры"; clientConn.close(); return; }
                    myId = id;
                    beginClientSession(seed, surv, todv, x, y, z, hostName, zb);
                    spawnX = rsx; spawnY = rsy; spawnZ = rsz;
                    break;
                }
                case Net.S_SKIN: {
                    int sid = in.readInt(), len = in.readInt();
                    if (len <= 0 || len > 30000) return;
                    byte[] b = new byte[len];
                    in.readFully(b);
                    int[] sk = decodeSkin(b);
                    if (sk != null) skins.put(sid, sk);
                    break;
                }
                case Net.S_TP:
                    pl.x = in.readFloat(); pl.y = in.readFloat(); pl.z = in.readFloat(); pl.vy = 0;
                    break;
                case Net.S_FLAGS:
                    weather = in.readByte();
                    pvp = in.readByte() != 0;
                    break;
                case Net.S_SLEEP:
                    sleepN = in.readInt();
                    sleepTotal = in.readInt();
                    break;
                case Net.S_TILE: {
                    if (gameWorld == null) return;
                    int x = in.readInt(), y = in.readInt(), z = in.readInt(), type = in.readInt(), n = in.readInt();
                    Tile t = new Tile(type);
                    t.x = x; t.y = y; t.z = z;
                    for (int i = 0; i < n; i++) {
                        int it = in.readInt(), ct = in.readInt();
                        if (i < t.item.length) { t.item[i] = it; t.cnt[i] = ct; }
                    }
                    t.burn = in.readFloat(); t.burnMax = in.readFloat(); t.prog = in.readFloat();
                    if (panel == 3 && x == uiTX && y == uiTY && z == uiTZ) uiTile = t;
                    break;
                }
                case Net.S_INV: {
                    if (gameWorld == null) return;
                    PData pdn = new PData();
                    readInvInto(in, pdn);
                    applyData(pdn);
                    msg = "Ваши вещи возвращены";
                    msgT = 3;
                    break;
                }
                case Net.S_PLAYERS: {
                    if (gameWorld == null) return;
                    int n = in.readInt();
                    java.util.Set<Integer> seen = new java.util.HashSet<>();
                    for (int i = 0; i < n; i++) {
                        int id = in.readInt();
                        String name = in.readUTF();
                        double x = in.readFloat(), y = in.readFloat(), z = in.readFloat(), yw = in.readFloat(), pt = in.readFloat();
                        boolean cr = in.readByte() != 0;
                        if (id == myId) continue;
                        RPlayer r = remotes.get(id);
                        if (r == null) {
                            r = new RPlayer();
                            r.id = id;
                            r.x = x; r.y = y; r.z = z; r.yaw = yw;
                            remotes.put(id, r);
                        }
                        r.name = name;
                        r.creator = cr;
                        r.tx = x; r.ty = y; r.tz = z; r.tyaw = yw; r.tpitch = pt;
                        seen.add(id);
                    }
                    remotes.keySet().retainAll(seen);
                    break;
                }
                case Net.S_MOBS: {
                    if (gameWorld == null) return;
                    int n = in.readInt();
                    Map<Integer, Mob> have = new HashMap<>();
                    for (Mob m : mobs) have.put(m.id, m);
                    List<Mob> nl = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        int id = in.readInt(), type = in.readByte();
                        double x = in.readFloat(), y = in.readFloat(), z = in.readFloat(), ang = in.readFloat(), ph = in.readFloat();
                        double sw = in.readByte() / 100.0;
                        boolean hu = in.readByte() != 0;
                        if (type < 0 || type >= MobModels.TYPES) continue;
                        Mob m = have.get(id);
                        if (m == null || m.type != type) {
                            m = new Mob(type);
                            m.id = id;
                            m.x = x; m.y = y; m.z = z; m.ang = ang;
                        }
                        m.nx = x; m.ny = y; m.nz = z; m.tang = ang;
                        m.phase = ph; m.swing = sw;
                        if (hu) m.hurt = 0.3;
                        nl.add(m);
                    }
                    mobs.clear();
                    mobs.addAll(nl);
                    break;
                }
                case Net.S_DROPS: {
                    if (gameWorld == null) return;
                    int n = in.readInt();
                    Map<Integer, Drop> have = new HashMap<>();
                    for (Drop o : drops) have.put(o.id, o);
                    List<Drop> nl = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        int id = in.readInt(), item = in.readInt(), cnt = in.readInt();
                        double x = in.readFloat(), y = in.readFloat(), z = in.readFloat();
                        if (item <= 0 || item >= NITEM) continue;
                        Drop dr = have.get(id);
                        if (dr == null) { dr = new Drop(item, cnt); dr.id = id; dr.x = x; dr.y = y; dr.z = z; }
                        dr.item = item; dr.cnt = cnt;
                        dr.nx = x; dr.ny = y; dr.nz = z;
                        nl.add(dr);
                    }
                    drops.clear();
                    drops.addAll(nl);
                    break;
                }
                case Net.S_BLOCK: {
                    if (gameWorld == null) return;
                    int x = in.readInt(), y = in.readInt(), z = in.readInt(), id = in.readInt();
                    if (y >= 0 && y < WY && id >= 0 && id < NITEM) gameWorld.edit(x, y, z, id);
                    break;
                }
                case Net.S_CHAT:
                    chatAdd(in.readUTF());
                    break;
                case Net.S_TIME:
                    tod = in.readFloat();
                    break;
                case Net.S_HURT: {
                    int dmg = in.readInt();
                    double kx = in.readFloat(), kz = in.readFloat();
                    if (dmg < 0) { hp = MAXHP; break; }
                    if (state == State.PLAYING || state == State.PAUSED) {
                        damage(dmg);
                        kbx = kx; kbz = kz;
                        pl.vy = Math.max(pl.vy, 4);
                    }
                    break;
                }
                case Net.S_LOOT: {
                    int n = Math.min(16, in.readInt());
                    List<int[]> loot = new ArrayList<>();
                    for (int i = 0; i < n; i++) loot.add(new int[]{in.readInt(), in.readInt()});
                    giveLoot(loot);
                    snd("pop");
                    break;
                }
                case Net.S_KICK:
                    kickReason = in.readUTF();
                    break;
                default:
                    break;
            }
        } catch (IOException e) {
            if (clientConn != null) clientConn.close();
        }
    }

    void beginClientSession(int seed, boolean surv, double todv, double x, double y, double z, String hostName, byte[] zb) throws IOException {
        survival = surv;
        if (gameWorld != null) gameWorld.stop();
        World nw = new World(seed, viewR, saver);
        try (DataInputStream gz = new DataInputStream(new java.util.zip.GZIPInputStream(new ByteArrayInputStream(zb)))) {
            int n = gz.readInt();
            for (int i = 0; i < n; i++) {
                int cx = gz.readInt(), cz = gz.readInt();
                byte[] b = new byte[CS * WY * CS];
                gz.readFully(b);
                Chunk c = new Chunk(cx, cz, b);
                c.modified = true;
                nw.saved.put(World.key(cx, cz), c);
            }
        }
        spawnX = x; spawnY = y; spawnZ = z;
        hasBed = false;
        nw.fx = x;
        nw.fz = z;
        nw.loadAround(3);
        nw.start();
        gameWorld = nw;
        world = nw;
        pl.x = x; pl.y = y; pl.z = z;
        pl.vx = pl.vy = pl.vz = 0;
        yaw = 0; pitch = 0; kbx = kbz = 0;
        hp = MAXHP;
        Arrays.fill(inv, 0);
        pick = axe = sword = 0;
        panel = 0;
        air = 10;
        food = 20;
        mobs.clear();
        drops.clear();
        remotes.clear(); skins.clear(); skinPngs.clear(); skinSent = false;
        initHotbar();
        tod = todv;
        fly = false;
        lmb = false;
        mining = false;
        msg = "Вы в мире игрока " + hostName + ". T — чат";
        msgT = 4;
        chatLog.clear();
        chatTime.clear();
        chatAdd("Подключено к " + hostName);
        joining = false;
        joinStatus = "";
        menuNote = "";
        netT = 0;
        Arrays.fill(keys, false);
        state = State.PLAYING;
        capture();
    }

    /** Плавное движение других игроков (и хост, и клиент). */
    void smoothRemotes(double dt) {
        double k = Math.min(1, dt * 12);
        for (RPlayer r : remotes.values()) {
            double ox = r.x, oz = r.z;
            r.x += (r.tx - r.x) * k;
            r.y += (r.ty - r.y) * k;
            r.z += (r.tz - r.z) * k;
            r.yaw += wrapAng(r.tyaw - r.yaw) * k;
            r.pitch += (r.tpitch - r.pitch) * k;
            double sp = Math.hypot(r.x - ox, r.z - oz) / Math.max(dt, 1e-3);
            r.swing += ((sp > 0.8 ? 1 : 0) - r.swing) * Math.min(1, dt * 10);
            if (sp > 0.8) r.phase += dt * Math.min(sp, 6) * 3.4;
        }
    }

    /** Один шаг сети на клиенте: отправка нашего положения, плавное движение мобов и игроков. */
    void clientTick(double dt) {
        smoothRemotes(dt);
        updateWeather(dt);
        if (!skinSent && mySkinPng != null && gameWorld != null) { skinSent = true; clientConn.send(new Net.W(Net.C_SKIN).i(mySkinPng.length).raw(mySkinPng).done()); }
        if (sleeping && daylight >= 0.5) sleeping = false;
        double k = Math.min(1, dt * 12);
        for (Mob m : mobs) {
            m.x += (m.nx - m.x) * k;
            m.y += (m.ny - m.y) * k;
            m.z += (m.nz - m.z) * k;
            m.ang += wrapAng(m.tang - m.ang) * k;
            m.hurt -= dt;
        }
        for (Drop d : drops) {
            d.x += (d.nx - d.x) * k;
            d.y += (d.ny - d.y) * k;
            d.z += (d.nz - d.z) * k;
            d.age += dt;
        }
        invSyncT += dt;
        if (invSyncT >= 1.0 && clientConn != null) {                  // раз в секунду, если что-то изменилось
            invSyncT = 0;
            int hh = invHash();
            if (hh != lastInvHash) { lastInvHash = hh; clientConn.send(invPacket(Net.C_INV, myData())); }
        }
        netT += dt;
        if (netT >= 0.05 && clientConn != null) {
            netT = 0;
            clientConn.sendLossy(new Net.W(Net.C_STATE).f(pl.x).f(pl.y).f(pl.z).f(yaw).f(pitch).done());
        }
    }

    /** Сеть, пока игрок в паузе или мёртв: мир для остальных не должен замирать. */
    void idleNet(double dt) {
        tod = (tod + dt * todRate(tod) * timeK) % 1;
        calcSky();
        if (isClient()) clientTick(dt);
        else if (server != null) {
            updateMobs(dt);
            updateDrops(dt);
            updateTiles(dt);
            updateCrops(dt);
            sleepTick(dt);
            updateWeather(dt);
            smoothRemotes(dt);
            hostTick(dt);
        }
    }

    void sendChat() {
        String t = chatText.trim();
        chatOpen = false;
        chatText = "";
        if (t.isEmpty()) return;
        if (t.length() > 120) t = t.substring(0, 120);
        if (t.startsWith("/")) {
            if (server != null) runCommand(t);
            else chatAdd("Команды доступны только хосту");
            return;
        }
        if (server != null) {
            String line = tagName(myName, selfCreator()) + ": " + t;
            chatAdd(line);
            server.broadcast(new Net.W(Net.S_CHAT).s(line).done(), null);
        } else if (clientConn != null) clientConn.send(new Net.W(Net.C_CHAT).s(t).done());
    }

    // ====================================================================
    //  Сохранение и настройки
    // ====================================================================
    static final int SAVE_MAGIC = 0x4D430005, SAVE_MAGIC_V4 = 0x4D430004, SAVE_MAGIC_OLD = 0x4D430003;
    boolean hasSave;
    String saveInfo = "";
    double saveT;
    final AtomicBoolean saving = new AtomicBoolean();

    static File dataDir() {
        File d = new File(System.getProperty("user.home"), ".minicraft");
        d.mkdirs();
        return d;
    }

    static String slot = "world1";

    static File worldsDir() {
        File d = new File(dataDir(), "worlds");
        d.mkdirs();
        return d;
    }

    static File saveFile() { return new File(worldsDir(), slot + ".dat"); }

    static String disp(String sl) { return sl.startsWith("world") ? "Мир " + sl.substring(5) : sl; }

    /** Старое единственное сохранение переезжает в список миров. */
    static void migrateLegacy() {
        try {
            File o = new File(dataDir(), "save.dat"), op = new File(dataDir(), "players.dat");
            File n = new File(worldsDir(), "world1.dat");
            if (o.exists() && !n.exists()) {
                Files.move(o.toPath(), n.toPath());
                if (op.exists()) Files.move(op.toPath(), new File(worldsDir(), "world1.players").toPath());
            }
        } catch (Exception ignored) { }
    }

    static List<String> worldNames() {
        File[] fs = worldsDir().listFiles((d, nm) -> nm.endsWith(".dat"));
        List<File> l = new ArrayList<>();
        if (fs != null) l.addAll(Arrays.asList(fs));
        l.sort((x, y) -> Long.compare(y.lastModified(), x.lastModified()));
        List<String> r = new ArrayList<>();
        for (File f : l) r.add(f.getName().substring(0, f.getName().length() - 4));
        return r;
    }

    static String freeSlot() {
        for (int i = 1; ; i++) if (!new File(worldsDir(), "world" + i + ".dat").exists()) return "world" + i;
    }

    List<String> worlds = new ArrayList<>(), worldInfos = new ArrayList<>();
    int worldPage;
    String delConfirm = "";

    void deleteWorld(String sl) {
        new File(worldsDir(), sl + ".dat").delete();
        new File(worldsDir(), sl + ".players").delete();
        refreshSaveInfo();
    }

    static final class SaveData {
        int seed, hp, sel, pick, axe, sword;
        boolean survival, fly, oldGen;
        double px, py, pz, yaw, pitch, spx, spy, spz, tod;
        final int[] inv = new int[NITEM];
        final int[] hot = new int[9];
        boolean hasHot, hasBed;
        int bx, by, bz;
        final List<int[]> tiles = new ArrayList<>();            // {x,y,z,тип,n,горение*100,макс*100,прогресс*100, предмет,кол-во...}
        final List<Chunk> chunks = new ArrayList<>();
    }

    /** Снимок состояния игры (вызывать из игрового потока). */
    SaveData snapshot() {
        SaveData sd = new SaveData();
        sd.seed = gameWorld.seed;
        sd.survival = survival;
        boolean dead = state == State.DEAD;
        sd.px = dead ? spawnX : pl.x; sd.py = dead ? spawnY : pl.y; sd.pz = dead ? spawnZ : pl.z;
        sd.yaw = yaw; sd.pitch = pitch;
        sd.spx = spawnX; sd.spy = spawnY; sd.spz = spawnZ;
        sd.hp = dead ? MAXHP : hp;
        sd.sel = sel; sd.pick = pick; sd.axe = axe; sd.sword = sword;
        sd.tod = tod; sd.fly = fly;
        System.arraycopy(inv, 0, sd.inv, 0, NITEM);
        System.arraycopy(hot, 0, sd.hot, 0, 9);
        sd.hasHot = true;
        sd.hasBed = hasBed; sd.bx = bedX; sd.by = bedY; sd.bz = bedZ;
        for (Tile t : gameWorld.tiles.values()) {
            int[] a = new int[8 + t.item.length * 2];
            a[0] = t.x; a[1] = t.y; a[2] = t.z; a[3] = t.type; a[4] = t.item.length;
            a[5] = (int) (t.burn * 100); a[6] = (int) (t.burnMax * 100); a[7] = (int) (t.prog * 100);
            for (int i = 0; i < t.item.length; i++) { a[8 + i * 2] = t.item[i]; a[9 + i * 2] = t.cnt[i]; }
            sd.tiles.add(a);
        }
        for (Chunk c : gameWorld.modifiedChunks()) sd.chunks.add(new Chunk(c.cx, c.cz, c.b.clone()));
        return sd;
    }

    static void writeSave(SaveData sd) {
        File f = saveFile(), tmp = new File(f.getParentFile(), "save.tmp");
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp))))) {
            o.writeInt(SAVE_MAGIC);
            o.writeInt(sd.seed);
            o.writeBoolean(sd.survival);
            o.writeDouble(sd.px); o.writeDouble(sd.py); o.writeDouble(sd.pz);
            o.writeDouble(sd.yaw); o.writeDouble(sd.pitch);
            o.writeDouble(sd.spx); o.writeDouble(sd.spy); o.writeDouble(sd.spz);
            o.writeDouble(sd.tod);
            o.writeInt(sd.hp); o.writeInt(sd.sel);
            o.writeInt(sd.pick); o.writeInt(sd.axe); o.writeInt(sd.sword);
            o.writeBoolean(sd.fly);
            o.writeInt(sd.inv.length);
            for (int v : sd.inv) o.writeInt(v);
            for (int v : sd.hot) o.writeInt(v);
            o.writeInt(sd.chunks.size());
            for (Chunk c : sd.chunks) { o.writeInt(c.cx); o.writeInt(c.cz); o.write(c.b); }
            o.writeBoolean(sd.hasBed); o.writeInt(sd.bx); o.writeInt(sd.by); o.writeInt(sd.bz);
            o.writeInt(sd.tiles.size());
            for (int[] a : sd.tiles) { o.writeInt(a.length); for (int v : a) o.writeInt(v); }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try { Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING); } catch (IOException e2) { e2.printStackTrace(); }
        }
    }

    /** Читает сохранение. headerOnly - только сид и режим (для надписи в меню). */
    static SaveData readSave(boolean headerOnly) { return readSaveFile(saveFile(), headerOnly); }

    static SaveData readSaveFile(File f, boolean headerOnly) {
        if (!f.exists()) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f))))) {
            int magic = in.readInt();
            if (magic != SAVE_MAGIC && magic != SAVE_MAGIC_V4 && magic != SAVE_MAGIC_OLD) return null;
            SaveData sd = new SaveData();
            sd.oldGen = magic != SAVE_MAGIC;
            sd.seed = in.readInt();
            sd.survival = in.readBoolean();
            if (headerOnly) return sd;
            sd.px = in.readDouble(); sd.py = in.readDouble(); sd.pz = in.readDouble();
            sd.yaw = in.readDouble(); sd.pitch = in.readDouble();
            sd.spx = in.readDouble(); sd.spy = in.readDouble(); sd.spz = in.readDouble();
            sd.tod = in.readDouble();
            sd.hp = in.readInt(); sd.sel = in.readInt();
            sd.pick = in.readInt(); sd.axe = in.readInt(); sd.sword = in.readInt();
            sd.fly = in.readBoolean();
            int n = in.readInt();
            for (int i = 0; i < n; i++) { int v = in.readInt(); if (i < NITEM) sd.inv[i] = v; }
            if (magic != SAVE_MAGIC_OLD) { for (int i = 0; i < 9; i++) sd.hot[i] = in.readInt(); sd.hasHot = true; }
            int cn = in.readInt();
            for (int i = 0; i < cn; i++) {
                int cx = in.readInt(), cz = in.readInt();
                byte[] b = new byte[CS * WY * CS];
                in.readFully(b);
                sd.chunks.add(new Chunk(cx, cz, b));
            }
            if (magic == SAVE_MAGIC) {
                sd.hasBed = in.readBoolean(); sd.bx = in.readInt(); sd.by = in.readInt(); sd.bz = in.readInt();
                int tn = in.readInt();
                for (int i = 0; i < tn; i++) {
                    int len = in.readInt();
                    int[] a = new int[len];
                    for (int j = 0; j < len; j++) a[j] = in.readInt();
                    sd.tiles.add(a);
                }
            }
            return sd;
        } catch (Exception e) {
            return null;
        }
    }

    void refreshSaveInfo() {
        migrateLegacy();
        worlds = worldNames();
        worldInfos.clear();
        for (String w : worlds) {
            SaveData d = readSaveFile(new File(worldsDir(), w + ".dat"), true);
            worldInfos.add(d == null ? "повреждён" : (d.survival ? "Выживание" : "Творчество") + ", сид " + d.seed);
        }
        if (!worlds.contains(slot) && !worlds.isEmpty()) slot = worlds.get(0);
        SaveData h = readSave(true);
        hasSave = h != null;
        saveInfo = h == null ? "" : (h.survival ? "Выживание" : "Творчество") + ", сид " + h.seed;
    }

    void saveSync() {
        if (gameWorld == null || isClient()) return;
        writeSave(snapshot());
        savePlayers();
    }

    void saveAsync() {
        if (gameWorld == null || isClient() || !saving.compareAndSet(false, true)) return;
        final SaveData sd = snapshot();
        savePlayers();
        Thread t = new Thread(() -> {
            try { writeSave(sd); } finally { saving.set(false); }
        }, "autosave");
        t.setDaemon(true);
        t.start();
    }

    void loadConfig() {
        try {
            File f = new File(dataDir(), "config.properties");
            if (!f.exists()) return;
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(f)) { p.load(in); }
            viewR = Integer.parseInt(p.getProperty("view", "6"));
            if (viewR != 4 && viewR != 6 && viewR != 8) viewR = 6;
            saver = Boolean.parseBoolean(p.getProperty("saver", "true"));
            soundOn = Boolean.parseBoolean(p.getProperty("sound", "true"));
            myName = cleanName(p.getProperty("name", myName), 0);
            lastAddr = p.getProperty("addr", "");
            myUid = p.getProperty("uid", "");
            loadingOpts = true;
            acctProof = p.getProperty("proof", "");
            proofNick = p.getProperty("proofnick", "");
            skinPath = p.getProperty("skin", "");
            ach.clear();
            for (String a : p.getProperty("ach", "").split(",")) if (!a.isEmpty()) ach.add(a);
            for (Opt o : opts) { String sv = p.getProperty("o." + o.key); if (sv != null) o.set(Integer.parseInt(sv)); }
            loadingOpts = false;
            applyOpts();
            slot = p.getProperty("slot", slot).replaceAll("[^A-Za-z0-9_]", "");
            if (slot.isEmpty()) slot = "world1";
        } catch (Exception ignored) { }
    }

    void saveConfig() {
        try {
            Properties p = new Properties();
            p.setProperty("view", String.valueOf(viewR));
            p.setProperty("saver", String.valueOf(saver));
            p.setProperty("sound", String.valueOf(soundOn));
            p.setProperty("name", myName);
            p.setProperty("addr", lastAddr);
            p.setProperty("uid", myUid);
            p.setProperty("slot", slot);
            p.setProperty("proof", acctProof);
            p.setProperty("proofnick", proofNick);
            p.setProperty("skin", skinPath);
            p.setProperty("ach", String.join(",", ach));
            for (Opt o : opts) p.setProperty("o." + o.key, String.valueOf(o.get()));
            try (FileOutputStream out = new FileOutputStream(new File(dataDir(), "config.properties"))) { p.store(out, "MiniCraft"); }
        } catch (Exception ignored) { }
    }

    void shutdownNow() {
        saveConfig();
        System.exit(0);
    }

    /** Закрытие окна: сохраняем мир, если идёт игра. */
    void shutdown() {
        synchronized (lock) {
            if (gameWorld != null && state != State.MENU && state != State.SPLASH) saveSync();
            saveConfig();
        }
        System.exit(0);
    }

    // ====================================================================
    //  Игровая логика
    // ====================================================================
    void update(double dt) {
        switch (state) {
            case SPLASH:
                splashT += dt;
                orbitYaw += dt * 0.08;
                tod = (tod + dt / DAY_LEN) % 1;
                if (splashT > SPLASH_LEN) state = State.MENU;
                break;
            case MENU:
                orbitYaw += dt * 0.08;
                tod = (tod + dt / DAY_LEN) % 1;
                if (joining && animT - joinStart > 15) { cancelJoin(); joinStatus = "Хост не ответил. Проверьте адрес и порт " + Net.PORT; }
                break;
            case PLAYING:
                updatePlaying(dt);
                break;
            default:                                          // пауза или смерть
                if (isNet() && gameWorld != null) idleNet(dt);
                break;
        }
        animT += dt;
        if (state == State.PLAYING && gameWorld != null) {
            saveT += dt;
            if (saveT > saveEvery) { saveT = 0; saveAsync(); }
        }
        if (msgT > 0) msgT -= dt;
        if (hurtT > 0) hurtT -= dt;
    }

    double[] lookDir() {
        double cp = Math.cos(pitch);
        return new double[]{cp * Math.sin(yaw), Math.sin(pitch), cp * Math.cos(yaw)};
    }

    void updatePlaying(double dt) {
        tod = (tod + dt * todRate(tod) * timeK) % 1;
        double fwd = 0, str = 0;
        final boolean[] keys = panel == 1 || panel == 3 || sleeping ? new boolean[256] : this.keys;     // в окне инвентаря ходить нельзя
        if (keys[KeyEvent.VK_W]) fwd += 1;
        if (keys[KeyEvent.VK_S]) fwd -= 1;
        if (keys[KeyEvent.VK_D]) str += 1;
        if (keys[KeyEvent.VK_A]) str -= 1;
        double sy = Math.sin(yaw), cyw = Math.cos(yaw);
        double mx = sy * fwd + cyw * str, mz = cyw * fwd - sy * str;
        double len = Math.sqrt(mx * mx + mz * mz);
        if (len > 1) { mx /= len; mz /= len; }
        boolean inW = !fly && world.isWater(pl.x, pl.y + 0.5, pl.z);
        boolean headW = world.isWater(pl.x, pl.y + 1.62, pl.z);
        double speed = fly ? 12 : inW ? 3.2 : ((keys[KeyEvent.VK_CONTROL] || sprintAlways) && fwd > 0 && (!survival || !hungerOn || food > 6)) ? 7.2 : 5;
        kbx -= kbx * Math.min(1, dt * 8);
        kbz -= kbz * Math.min(1, dt * 8);
        pl.vx = mx * speed + kbx;
        pl.vz = mz * speed + kbz;
        boolean space = keys[KeyEvent.VK_SPACE];
        if (fly) {
            pl.vy = ((space ? 1 : 0) - (keys[KeyEvent.VK_SHIFT] ? 1 : 0)) * speed;
        } else if (inW) {                                     // плавание
            pl.vy -= 9 * dt;
            if (space) pl.vy += 26 * dt;
            if (keys[KeyEvent.VK_SHIFT]) pl.vy -= 12 * dt;
            pl.vy *= Math.max(0, 1 - 3.0 * dt);
            pl.vy = Math.max(-4, Math.min(4.2, pl.vy));
            if (pl.blocked && space && !headW) pl.vy = 9;     // выпрыгнуть на берег
        } else {
            pl.vy = Math.max(-50, pl.vy - 28 * dt);
            if (space && pl.onGround) pl.vy = 9;
        }
        pl.step(dt);
        if (inW || headW) waterT = 0.4; else waterT -= dt;
        if (inW && !wasInW && waterT > 0) snd("splash", 0, 0.9);
        wasInW = inW;
        double hs = Math.hypot(pl.vx - kbx, pl.vz - kbz);               // шаги
        if (!fly && hs > 0.5 && (pl.onGround || inW)) {
            stepAcc += hs * dt;
            if (stepAcc > (inW ? 2.6 : 2.1)) {
                stepAcc = 0;
                if (inW) snd("splash", 0, 0.35);
                else {
                    int under = world.get((int) Math.floor(pl.x), (int) Math.floor(pl.y - 0.2), (int) Math.floor(pl.z));
                    snd("step_" + matName(under), 0, 0.5);
                }
            }
        }
        cactusT -= dt;
        protT -= dt;
        if (survival && cactusT <= 0) {
            outer:
            for (double oy = 0.2; oy < 1.7; oy += 0.7)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        if (world.get((int) Math.floor(pl.x + dx * 0.4), (int) Math.floor(pl.y + oy), (int) Math.floor(pl.z + dz * 0.4)) == B_CACTUS) {
                            damage(1); kbx = -dx * 4; kbz = -dz * 4; cactusT = 0.6; break outer;
                        }
        }
        if (survival && !fly && pl.impact < -14 && waterT <= 0) {
            int dmg = (int) Math.ceil(pl.impact * pl.impact / 56 - 3);
            if (dmg > 0) damage(dmg);
        }
        if (survival) {                                       // воздух под водой
            if (headW) {
                air -= dt;
                if (air <= 0) { air = 0; drownT -= dt; if (drownT <= 0) { damage(2); drownT = 1; } }
            } else { air = Math.min(10, air + dt * 5); drownT = 0.3; }
        } else air = 10;

        // двигаем центр подгрузки чанков вслед за игроком и его взглядом
        world.fx = pl.x;
        world.fz = pl.z;
        world.dirx = Math.sin(yaw);
        world.dirz = Math.cos(yaw);

        if (survival) for (int j = 0; j < hot.length; j++) if (hot[j] > 0 && inv[hot[j]] <= 0) hot[j] = 0;      // закончился - убираем с панели
        calcSky();
        if (state == State.PLAYING) {
            tickFuses(dt);
            int fxp = (int) Math.floor(pl.x), fzp = (int) Math.floor(pl.z);
            boolean inP = world.get(fxp, (int) Math.floor(pl.y + 0.3), fzp) == B_PORTAL || world.get(fxp, (int) Math.floor(pl.y + 1.2), fzp) == B_PORTAL;
            if (inP) {
                if (portalArmed) { portalT += dt; if (portalT >= 1.2) { portalT = 0; portalArmed = false; travel(); } }
            } else { portalT = 0; portalArmed = true; }
            if (survival && world.get(fxp, (int) Math.floor(pl.y - 0.05), fzp) == B_MAGMA) {
                magT += dt;
                if (magT >= 0.5) { magT = 0; damage(2); }
            } else magT = 0;
        }
        if (survival) {
            if (hungerOn) {
                double mvs = Math.hypot(pl.vx - kbx, pl.vz - kbz);
                food = Math.max(0, food - dt * (0.012 + (mvs > 0.5 ? 0.018 : 0) + (mvs > 6 ? 0.05 : 0)));
                if (food <= 0) {
                    starveT += dt;
                    if (starveT >= 4) { starveT = 0; if (hp > 1) damage(1); }
                } else starveT = 0;
            }
            if (hp < MAXHP && hp > 0 && (!hungerOn || food >= 14)) {
                regenT += dt * (hungerOn && food >= 19 ? 1.5 : 1);
                if (regenT >= 5) { hp++; regenT = 0; if (hungerOn) food = Math.max(0, food - 0.6); }
            }
        }
        updateMinimap(dt);
        if (panel == 3 && (uiTile == null || world.get(uiTX, uiTY, uiTZ) != uiTile.type)) closePanel();      // контейнер сломали
        if (panel == 3 && isClient()) {                                                                          // обновляем содержимое
            uiPoll += dt;
            if (uiPoll > 0.4) { uiPoll = 0; clientConn.send(new Net.W(Net.C_TILE).b(0).i(uiTX).i(uiTY).i(uiTZ).i(0).i(0).i(0).done()); }
        }
        if (isClient()) clientTick(dt);
        else {
            updateMobs(dt);
            updateDrops(dt);
            updateTiles(dt);
            updateCrops(dt);
            sleepTick(dt);
            updateWeather(dt);
            if (server != null) { smoothRemotes(dt); hostTick(dt); }
        }
        updateTarget();
        atkCd -= dt;
        swingT = Math.max(0, swingT - dt * 3.2);
        equipT = Math.max(0, equipT - dt * 4);
        critT = Math.max(0, critT - dt * 1.6);
        if (sel != lastHandSel) { if (lastHandSel >= 0) equipT = 1; lastHandSel = sel; }
        if (lmb) handleHold(dt);
        else { mining = false; mineProg = 0; mineT = 0; }
        if (state == State.PLAYING) updateTarget();
    }

    double protT, portalT, magT, swingT, equipT, critT;
    int lastHandSel = -1;
    boolean portalArmed = true;

    /** Переход между Обычным и Нижним миром: у каждого портала пара на тех же X/Z (со сдвигом NX). */
    void travel() {
        boolean toOver = pl.x >= NT;
        int tx0 = (int) Math.floor(pl.x) + (toOver ? -NX : NX), tz0 = (int) Math.floor(pl.z);
        world.fx = tx0; world.fz = tz0;
        world.loadAround(3);
        int px = Integer.MIN_VALUE, py = 0, pz = 0;
        for (int y = 1; y < WY - 1 && px == Integer.MIN_VALUE; y++)
            for (int dx = -3; dx <= 3 && px == Integer.MIN_VALUE; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (world.get(tx0 + dx, y, tz0 + dz) == B_PORTAL) { px = tx0 + dx; py = y; pz = tz0 + dz; break; }
        if (px == Integer.MIN_VALUE) {
            int ty = toOver ? Math.max(3, Math.min(50, world.surfaceY(tx0, tz0) + 1)) : 28;
            if (clientConn != null) clientConn.send(new Net.W(Net.C_STATE).f(tx0 + 0.5).f(ty).f(tz0 + 0.5).f(yaw).f(pitch).done());   // хост должен знать новое место до правок блоков
            for (int dx = -1; dx <= 2; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    world.edit(tx0 + dx, ty - 1, tz0 + dz, B_OBSI);
                    for (int y = ty; y <= ty + 4; y++) world.edit(tx0 + dx, y, tz0 + dz, 0);
                }
            for (int dx = -1; dx <= 2; dx++)
                for (int y = ty; y <= ty + 3; y++) {
                    boolean edge = dx == -1 || dx == 2 || y == ty + 3;
                    world.edit(tx0 + dx, y, tz0, edge ? B_OBSI : B_PORTAL);
                }
            px = tx0; py = ty; pz = tz0;
            if (isNet()) for (int dx = -1; dx <= 2; dx++) for (int y = ty - 1; y <= ty + 3; y++) netBlock(tx0 + dx, y, tz0, world.get(tx0 + dx, y, tz0));
        }
        pl.x = px + 0.5; pl.y = py; pl.z = pz + 0.5;
        pl.vx = pl.vy = pl.vz = 0;
        kbx = kbz = 0;
        world.fx = pl.x; world.fz = pl.z;
        if (!isNet()) mobs.clear();
        protT = Math.max(protT, 2);
        unlock("nether");
        msg = toOver ? "Вы вернулись в Обычный мир" : "Нижний мир";
        msgT = 3;
        snd("portal");
    }

    /** Поджигаем рамку из обсидиана: заливка свободной плоскости внутри рамки. */
    final java.util.concurrent.ConcurrentHashMap<Long, double[]> fuses = new java.util.concurrent.ConcurrentHashMap<>();

    void ignite(int x, int y, int z, double t) {
        if (fuses.containsKey(tkey(x, y, z))) return;
        fuses.put(tkey(x, y, z), new double[]{x, y, z, t});
        unlock("tnt");
        snd("fuse");
    }

    void tickFuses(double dt) {
        for (java.util.Iterator<java.util.Map.Entry<Long, double[]>> it = fuses.entrySet().iterator(); it.hasNext(); ) {
            double[] f = it.next().getValue();
            f[3] -= dt;
            if (f[3] > 0) continue;
            it.remove();
            int x = (int) f[0], y = (int) f[1], z = (int) f[2];
            if (world.get(x, y, z) != B_TNT) continue;
            world.set(x, y, z, 0);
            netBlock(x, y, z, 0);
            explode(x + 0.5, y + 0.5, z + 0.5);
        }
    }

    void explode(double cx, double cy, double cz) {
        final int R = 3;
        int bx = (int) Math.floor(cx), by = (int) Math.floor(cy), bz = (int) Math.floor(cz);
        for (int dx = -R; dx <= R; dx++)
            for (int dy = -R; dy <= R; dy++)
                for (int dz = -R; dz <= R; dz++) {
                    if (dx * dx + dy * dy + dz * dz > R * R + 1) continue;
                    int x = bx + dx, y = by + dy, z = bz + dz;
                    if (y < 1 || y >= WY - 1) continue;
                    int b = world.get(x, y, z);
                    if (b <= 0 || b == 7 || b == WATER || b == B_OBSI || b == B_PORTAL) continue;
                    if (b == B_TNT) { fuses.putIfAbsent(tkey(x, y, z), new double[]{x, y, z, 0.3 + rnd.nextDouble() * 0.5}); continue; }
                    if (!isClient() && isTileBlock(b)) breakTile(x, y, z);
                    world.set(x, y, z, 0);
                    netBlock(x, y, z, 0);
                }
        double dp = Math.sqrt((pl.x - cx) * (pl.x - cx) + (pl.y + 0.9 - cy) * (pl.y + 0.9 - cy) + (pl.z - cz) * (pl.z - cz));
        snd("boom", dp, 1);
        if (dp < 7) {
            int dmg = (int) Math.round(12 * (1 - dp / 7));
            double ddx = pl.x - cx, ddz = pl.z - cz, dd = Math.max(0.1, Math.hypot(ddx, ddz));
            if (dmg > 0) damage(dmg);
            kbx = ddx / dd * 9 * (1 - dp / 7); kbz = ddz / dd * 9 * (1 - dp / 7);
            pl.vy = Math.max(pl.vy, 6 * (1 - dp / 7));
        }
        if (!isClient()) {
            for (Mob m : new ArrayList<>(mobs)) {
                double dm = Math.sqrt((m.x - cx) * (m.x - cx) + (m.y + 0.9 - cy) * (m.y + 0.9 - cy) + (m.z - cz) * (m.z - cz));
                if (dm < 7) hitMob(m, Math.max(1, (int) Math.round(20 * (1 - dm / 7))), cx, cz, null);
            }
        }
        updateTarget();
    }

    boolean lightPortal() {
        int sx = tx + tnx, sy = ty + tny, sz = tz + tnz;
        for (int axis = 0; axis < 2; axis++) {
            java.util.ArrayList<int[]> cells = new java.util.ArrayList<>();
            java.util.HashSet<Long> seen = new java.util.HashSet<>();
            cells.add(new int[]{sx, sy, sz}); seen.add(tkey(sx, sy, sz));
            boolean ok = true;
            for (int i = 0; i < cells.size() && ok; i++) {
                int[] c = cells.get(i);
                int[][] dd = axis == 0 ? new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}} : new int[][]{{0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
                for (int[] d : dd) {
                    int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
                    int b = world.get(x, y, z);
                    if (b == B_OBSI) continue;
                    if (b != 0 && b != B_PORTAL) { ok = false; break; }
                    if (seen.add(tkey(x, y, z))) cells.add(new int[]{x, y, z});
                    if (cells.size() > 24) { ok = false; break; }
                }
            }
            if (ok && cells.size() >= 6) {
                for (int[] c : cells) { world.set(c[0], c[1], c[2], B_PORTAL); netBlock(c[0], c[1], c[2], B_PORTAL); }
                msg = "Портал зажжён!"; msgT = 2; snd("portal");
                return true;
            }
        }
        return false;
    }

    void damage(int d) {
        if (!survival || hp <= 0 || protT > 0) return;
        double blockCh = inv[I_ARM_D] > 0 ? 0.45 : inv[I_ARM_I] > 0 ? 0.25 : 0;
        if (blockCh > 0 && Math.random() < blockCh) { snd("clink"); return; }
        hp -= d;
        hurtT = 0.45;
        regenT = 0;
        wakeUp();
        snd("hurt");
        if (hp <= 0) {
            hp = 0;
            panel = 0;
            sleeping = false;
            spillInventory();
            state = State.DEAD;
            lmb = false;
            release();
        }
    }

    void updateTarget() {
        double[] d = lookDir();
        int[] o = new int[7];
        double ex = pl.x, ey = pl.y + 1.62, ez = pl.z;
        double t = world.cast(ex, ey, ez, d[0], d[1], d[2], REACH, o, new double[2]);
        hasTarget = t >= 0;
        if (hasTarget) { tx = o[0]; ty = o[1]; tz = o[2]; tnx = o[3]; tny = o[4]; tnz = o[5]; }
        targetMob = null;
        double best = 0;
        int[] ax = new int[2];
        for (Mob m : mobs) {
            double bw = Math.max(m.hw, 0.42);
            double tm = rayBox(ex, ey, ez, d[0], d[1], d[2], m.x - bw, m.y, m.z - bw, m.x + bw, m.y + m.h, m.z + bw, ax);
            if (tm >= 0 && tm <= 3.6 && (!hasTarget || tm < t) && (targetMob == null || tm < best)) {
                targetMob = m;
                best = tm;
            }
        }
        targetPl = null;
        if (pvp && survival) {
            double bp = 0;
            for (RPlayer r : remotes.values()) {
                double tm = rayBox(ex, ey, ez, d[0], d[1], d[2], r.x - 0.4, r.y, r.z - 0.4, r.x + 0.4, r.y + 1.8, r.z + 0.4, ax);
                if (tm >= 0 && tm <= 3.6 && (!hasTarget || tm < t) && (targetMob == null || tm < best) && (targetPl == null || tm < bp)) { targetPl = r; bp = tm; }
            }
            if (targetPl != null) targetMob = null;
        }
    }

    void hitPlayer(int victim, int dmg, double ax, double az) {
        if (!pvp || server == null) return;
        if (victim == 0) {
            double dx = pl.x - ax, dz = pl.z - az, dd = Math.max(0.01, Math.hypot(dx, dz));
            damage(dmg); kbx = dx / dd * 7; kbz = dz / dd * 7; pl.vy = Math.max(pl.vy, 4);
            return;
        }
        for (Net.Conn c : server.conns) if (c.ready && c.id == victim) {
            double dx = c.x - ax, dz = c.z - az, dd = Math.max(0.01, Math.hypot(dx, dz));
            sendHurt(c, dmg, dx / dd * 7, dz / dd * 7);
        }
    }

    /** Критический удар: игрок в воздухе и падает. */
    boolean isCrit() { return !pl.onGround && pl.vy < -0.1 && !world.isWater(pl.x, pl.y + 0.3, pl.z) && !sleeping; }

    void doCrit() { critT = 1; snd("crit", 0, 1); }

    void handleHold(double dt) {
        if (targetPl != null) {
            mining = false;
            mineProg = 0;
            if (atkCd <= 0) {
                int dmg = SWORD_DMG[sword];
                if (isCrit()) { dmg = (int) Math.ceil(dmg * 1.5); doCrit(); }
                swingT = 1;
                snd("hurt", 0, 0.6);
                if (isClient()) clientConn.send(new Net.W(Net.C_HIT).i(targetPl.id).i(dmg).done());
                else hitPlayer(targetPl.id, dmg, pl.x, pl.z);
                atkCd = 0.5;
            }
            return;
        }
        if (targetMob != null) {
            mining = false;
            mineProg = 0;
            if (atkCd <= 0) {
                Mob m = targetMob;
                int dmg = survival ? SWORD_DMG[sword] : 100;
                if (survival && isCrit()) { dmg = (int) Math.ceil(dmg * 1.5); doCrit(); }
                swingT = 1;
                snd("breakSoft", 0, 0.8);
                snd(m.hostile() ? "zhurt" : mobSound(m.type), Math.hypot(m.x - pl.x, m.z - pl.z), 0.9);
                if (isClient()) {                                 // урон считает хост
                    clientConn.send(new Net.W(Net.C_ATTACK).i(m.id).i(dmg).done());
                    m.hurt = 0.3;
                } else hitMob(m, dmg, pl.x, pl.z, null);
                atkCd = 0.45;
            }
            return;
        }
        if (!hasTarget) { mining = false; mineProg = 0; return; }
        int id = world.get(tx, ty, tz);
        if (id <= 0 || HARD[id] < 0) { mining = false; mineProg = 0; return; }
        if (!survival) {
            mineT -= dt;
            if (mineT <= 0) { harvest(tx, ty, tz, id); mineT = 0.2; }
            return;
        }
        if (!(mining && mineX == tx && mineY == ty && mineZ == tz)) {
            mining = true;
            mineX = tx; mineY = ty; mineZ = tz;
            mineProg = 0;
        }
        int tool = TOOLT[id];
        int tier = tool == 1 ? pick : tool == 2 ? axe : 0;
        if (swingT <= 0) swingT = 0.9;
        tickT -= dt;
        if (tickT <= 0) { tickT = 0.27; snd("tick", 0, 0.7); }
        mineProg += dt * TIERM[tier] / HARD[id];
        if (mineProg >= 1) {
            harvest(tx, ty, tz, id);
            mining = false;
            mineProg = 0;
        }
    }

    /** Удар по мобу. (ax, az) - откуда ударили, killer - игрок по сети (null - это мы). Работает только на хосте/в одиночной игре. */
    void hitMob(Mob m, int dmg, double ax, double az, Net.Conn killer) {
        m.hp -= dmg;
        m.hurt = 0.3;
        m.fleeT = m.hostile() ? 0 : 3.5;
        double dx = m.x - ax, dz = m.z - az, d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        m.kx = dx / d * 6;
        m.kz = dz / d * 6;
        m.vy = 5;
        if (m.hp <= 0) killMob(m, killer);
    }

    void killMob(Mob m, Net.Conn killer) {
        mobs.remove(m);
        if (!survival) return;
        List<int[]> loot = new ArrayList<>();
        switch (m.type) {
            case MobModels.PIG: case MobModels.COW: case MobModels.CHICKEN:
                loot.add(new int[]{I_MEAT, 1 + rnd.nextInt(2)});
                break;
            case MobModels.SHEEP:
                loot.add(new int[]{I_MEAT, 1});
                loot.add(new int[]{18, 1 + rnd.nextInt(2)});
                break;
            case MobModels.ZOMBIE:
                if (rnd.nextInt(3) == 0) loot.add(new int[]{I_COAL, 1});
                break;
            default:
                break;
        }
        if (loot.isEmpty()) return;
        if (killer != null) {
            Net.W w = new Net.W(Net.S_LOOT).i(loot.size());
            for (int[] l : loot) w.i(l[0]).i(l[1]);
            killer.send(w.done());
        } else giveLoot(loot);
    }

    void giveLoot(List<int[]> loot) {
        StringBuilder sb = new StringBuilder("+ ");
        for (int[] l : loot) {
            if (l[0] < 0 || l[0] >= NITEM) continue;
            addItem(l[0], l[1]);
            if (sb.length() > 2) sb.append(", ");
            sb.append(l[1]).append(" ").append(NAMES[l[0]]);
        }
        msg = sb.toString();
        msgT = 1.5;
    }

    /** Сообщает остальным игрокам об изменении блока. */
    void netBlock(int x, int y, int z, int id) {
        if (server != null) server.broadcast(new Net.W(Net.S_BLOCK).i(x).i(y).i(z).i(id).done(), null);
        else if (clientConn != null) clientConn.send(new Net.W(Net.C_BLOCK).i(x).i(y).i(z).i(id).done());
    }

    void harvest(int x, int y, int z, int id) {
        if (!isClient() && isTileBlock(id)) breakTile(x, y, z);
        world.set(x, y, z, 0);
        netBlock(x, y, z, 0);
        if (id == B_DOOR_L || id == B_DOOR_LO || id == B_DOOR_U || id == B_DOOR_UO) {          // вторая половина двери
            int oy = (id == B_DOOR_L || id == B_DOOR_LO) ? y + 1 : y - 1;
            int ob = world.get(x, oy, z);
            if (ob == B_DOOR_L || ob == B_DOOR_LO || ob == B_DOOR_U || ob == B_DOOR_UO) { world.set(x, oy, z, 0); netBlock(x, oy, z, 0); }
        }
        snd(breakSound(id));
        if (survival) {
            int tool = TOOLT[id];
            int tier = tool == 1 ? pick : tool == 2 ? axe : 0;
            if (tier >= NEEDT[id] && DROP[id] > 0) {
                int drop = DROP[id];
                addItem(drop, 1);
                msg = "+1 " + NAMES[drop];
                if (id == B_RIPE) addItem(I_SEEDS, 1 + rnd.nextInt(2));           // спелая пшеница даёт и семена
                if ((id == 1 && rnd.nextDouble() < 0.12) || (id == 5 && rnd.nextDouble() < 0.06)) { addItem(I_SEEDS, 1); msg = "+1 Семена"; }
            } else {
                msg = tool == 1 ? "Нужна кирка получше" : "Нужен инструмент получше";
            }
            msgT = 1.5;
        }
        updateTarget();
    }

    void placeBlock() {
        int id = hot[sel];
        if (id <= 0) return;
        if (id >= 32 && id < 48) {                                    // предметы не ставятся: еду едят, семена сажают
            if (foodValue(id) > 0) eatItem(id);
            else if (id == I_SEEDS) plantSeeds();
            return;
        }
        if (!hasTarget) return;
        if (survival && inv[id] <= 0) return;
        int x = tx + tnx, y = ty + tny, z = tz + tnz;
        int cur = world.get(x, y, z);
        if (y < 0 || y >= WY || (cur != 0 && cur != WATER)) return;
        boolean door = id == B_DOOR_L;
        if (door) {
            int up = world.get(x, y + 1, z);
            if (y + 1 >= WY || (up != 0 && up != WATER)) return;
            if (!world.solid(x, y - 1, z)) return;
        }
        world.set(x, y, z, id);
        if (door) world.set(x, y + 1, z, B_DOOR_U);
        if (pl.hit()) { world.set(x, y, z, cur); if (door) world.set(x, y + 1, z, 0); return; }
        if (survival) inv[id]--;
        netBlock(x, y, z, id);
        if (door) netBlock(x, y + 1, z, B_DOOR_U);
        snd("put_" + matName(id), 0, 0.85);
        swingT = 0.8;
        updateTarget();
    }


    // ====================================================================
    //  Инвентарь (экран E), выбрасывание и предметы на земле
    // ====================================================================
    void togglePanel() {
        if (panel == 1 || panel == 3) { closePanel(); return; }
        panel = 1;
        panelSel = 0;
        lmb = false;
        mining = false;
        release();
    }

    void closePanel() {
        boolean was = panel == 1 || panel == 3;
        panel = 0;
        if (was && state == State.PLAYING) capture();
    }

    /** Предмет, который сейчас под курсором в экране инвентаря (или 0). */
    int hoverItem() {
        if (panel != 1) return 0;
        InvL L = invL();
        int[] items = invItems();
        for (int i = 0; i < items.length && i < L.rows * 9; i++)
            if (inRect(mpx, mpy, L.gx + (i % 9) * (L.cell + L.gap), L.gy + (i / 9) * (L.cell + L.gap), L.cell, L.cell)) return items[i];
        for (int j = 0; j < 9; j++)
            if (inRect(mpx, mpy, L.hx + j * (L.cell + L.gap), L.hy, L.cell, L.cell)) return hot[j];
        return 0;
    }

    static boolean inRect(int px, int py, int x, int y, int w, int h) { return px >= x && px < x + w && py >= y && py < y + h; }

    void dropFromUi(boolean all) {
        int item = hot[sel];
        int hv = hoverItem();
        if (hv > 0) item = hv;
        dropItem(item, all);
    }

    /** Предмет, лежащий на земле. */
    class Drop extends Body {
        int id, item, cnt;
        double age, delay = 1.2, nx, ny, nz;

        Drop(int item, int cnt) {
            super(0.15, 0.32);
            this.item = item;
            this.cnt = cnt;
            this.id = ++dropSeq;
        }
    }

    final List<Drop> drops = new ArrayList<>();
    int dropSeq, sentDrops;

    void spawnDrop(int item, int cnt, double x, double y, double z, double vx, double vy, double vz) {
        if (item <= 0 || item >= NITEM || cnt <= 0) return;
        Drop d = new Drop(item, cnt);
        d.x = x; d.y = y; d.z = z; d.vx = vx; d.vy = vy; d.vz = vz;
        drops.add(d);
        while (drops.size() > 200) drops.remove(0);
    }

    /** Выбросить предмет из инвентаря: вперёд по взгляду. */
    void dropItem(int item, boolean all) {
        if (item <= 0 || item >= NITEM || state != State.PLAYING) return;
        int n = survival ? (all ? inv[item] : Math.min(1, inv[item])) : (all ? 16 : 1);
        if (n <= 0) { msg = "Нечего выбросить"; msgT = 1.2; return; }
        if (survival) inv[item] -= n;
        double[] d = lookDir();
        if (isClient()) clientConn.send(new Net.W(Net.C_DROP).i(item).i(n).done());
        else spawnDrop(item, n, pl.x + d[0] * 0.5, pl.y + 1.4, pl.z + d[2] * 0.5, d[0] * 5 + pl.vx * 0.3, d[1] * 5 + 2.5, d[2] * 5 + pl.vz * 0.3);
        msg = "Выброшено: " + n + " " + NAMES[item];
        msgT = 1.2;
        snd("pop");
    }

    /** Физика предметов и подбор ближайшим игроком (хост и одиночная игра). */
    void updateDrops(double dt) {
        while (drops.size() > dropCap) drops.remove(0);
        for (Iterator<Drop> it = drops.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            d.age += dt;
            d.delay -= dt;
            if (d.age > 600 || d.y < -10) { it.remove(); continue; }
            boolean inW = world.isWater(d.x, d.y + 0.15, d.z);
            if (inW) {
                d.vy = Math.max(-1.5, (d.vy - 12 * dt) * Math.max(0, 1 - 3 * dt));
                d.vx -= d.vx * Math.min(1, dt * 4);
                d.vz -= d.vz * Math.min(1, dt * 4);
            } else {
                d.vy = Math.max(-30, d.vy - 28 * dt);
                if (d.onGround) { d.vx -= d.vx * Math.min(1, dt * 8); d.vz -= d.vz * Math.min(1, dt * 8); }
            }
            d.step(dt);
            if (d.delay > 0) continue;
            Target t = nearestPlayer(d.x, d.z);
            if (t.d < 1.4 && Math.abs(t.y + 0.8 - d.y) < 1.8) {
                if (t.conn == null) { addItem(d.item, d.cnt); msg = "+" + d.cnt + " " + NAMES[d.item]; msgT = 1.2; snd("pop"); }
                else t.conn.send(new Net.W(Net.S_LOOT).i(1).i(d.item).i(d.cnt).done());
                it.remove();
            }
        }
    }

    // ---------- экран инвентаря ----------
    static final class InvL {
        int px, py, pw, ph, cell, gap, gx, gy, rows, hx, hy, rx, ry, rw, rh, rrows;
    }

    /** Предметы, показанные в сетке: в выживании всё, что есть в сумке; в творчестве - все блоки. */
    int[] invItems() {
        if (!survival) return PLACE_CREA;
        int n = 0;
        for (int id = 1; id < NITEM; id++) if (inv[id] > 0) n++;
        int[] r = new int[n];
        n = 0;
        for (int id = 1; id < NITEM; id++) if (inv[id] > 0) r[n++] = id;
        return r;
    }

    InvL invL() {
        InvL L = new InvL();
        int w = getWidth(), h = getHeight();
        L.cell = w >= 1000 && h >= 600 ? 46 : 38;
        L.gap = 4;
        int leftW = 9 * (L.cell + L.gap) - L.gap;
        int rightW = survival ? Math.max(260, Math.min(400, w - leftW - 100)) : 0;
        L.pw = 24 + leftW + (survival ? 24 + rightW : 0) + 24;
        int rowsMax = Math.max(2, (h - 76 - 28 - L.cell - 70) / (L.cell + L.gap));
        int need = (invItems().length + 8) / 9;
        L.rows = Math.max(2, Math.min(Math.min(8, rowsMax), need));
        int gridH = L.rows * (L.cell + L.gap);
        L.ph = 76 + gridH + 34 + L.cell + 40;
        if (survival) L.ph = Math.max(L.ph, 76 + 30 + 8 * 40 + 36);      // чтобы список крафта был виден
        L.ph = Math.min(L.ph, h - 16);
        L.px = (w - L.pw) / 2;
        L.py = Math.max(8, (h - L.ph) / 2);
        L.gx = L.px + 24;
        L.gy = L.py + 76;
        L.hx = L.gx;
        L.hy = L.py + L.ph - 40 - L.cell;
        L.rx = L.gx + leftW + 24;
        L.ry = L.py + 76;
        L.rw = rightW;
        L.rh = 40;
        L.rrows = Math.max(1, Math.min(RECIPES.length, (L.ph - 76 - 50) / L.rh));
        return L;
    }

    /** Клик мышью в экране инвентаря. */
    void invClick(int mx, int my, int button, boolean shift) {
        InvL L = invL();
        int[] items = invItems();
        for (int i = 0; i < items.length && i < L.rows * 9; i++) {
            if (!inRect(mx, my, L.gx + (i % 9) * (L.cell + L.gap), L.gy + (i / 9) * (L.cell + L.gap), L.cell, L.cell)) continue;
            int id = items[i];
            if (button != MouseEvent.BUTTON1) return;
            int target = sel;
            if (shift) { for (int j = 0; j < 9; j++) if (hot[j] == 0) { target = j; break; } }
            int other = -1;
            for (int j = 0; j < 9; j++) if (hot[j] == id) other = j;
            if (other >= 0 && other != target) { hot[other] = hot[target]; }
            hot[target] = id;
            if (shift) sel = target;
            snd("click");
            return;
        }
        for (int j = 0; j < 9; j++) {
            if (!inRect(mx, my, L.hx + j * (L.cell + L.gap), L.hy, L.cell, L.cell)) continue;
            if (button == MouseEvent.BUTTON3) hot[j] = 0;
            else sel = j;
            snd("click");
            return;
        }
        if (survival && button == MouseEvent.BUTTON1) {
            int first = Math.max(0, Math.min(panelSel - L.rrows / 2, RECIPES.length - L.rrows));
            for (int j = 0; j < L.rrows; j++) {
                if (inRect(mx, my, L.rx, L.ry + 30 + j * L.rh, L.rw, L.rh - 4)) { panelSel = first + j; doCraft(panelSel); return; }
            }
        }
    }

    void drawIcon(Graphics2D g, int cxx, int cyy, int s, int id, boolean bright) {
        if (id < 32 || id >= 48) { drawCube(g, cxx, cyy, s, id, bright); return; }
        double k = bright ? 1 : 0.35;
        Color c = new Color(scale(BASE[id], k)), dk = new Color(scale(BASE[id], 0.55 * k)), hi = new Color(scale(BASE[id], 1.35 * k));
        int h = s / 2;
        if (id == I_STICK) {
            g.setStroke(new BasicStroke(Math.max(3f, s / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(dk);
            g.drawLine(cxx - h + 3, cyy + h - 3, cxx + h - 3, cyy - h + 3);
            g.setStroke(new BasicStroke(Math.max(2f, s / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(c);
            g.drawLine(cxx - h + 3, cyy + h - 3, cxx + h - 3, cyy - h + 3);
        } else if (id == I_INGOT || id == I_GOLD) {
            int[] xs = {cxx - h, cxx + h - s / 5, cxx + h, cxx - h + s / 5}, ys = {cyy + h / 2, cyy + h / 2, cyy - h / 2 + 1, cyy - h / 2 + 1};
            g.setColor(dk);
            g.fillPolygon(new int[]{cxx - h, cxx + h - s / 5, cxx + h - s / 5, cxx - h}, new int[]{cyy + h / 2, cyy + h / 2, cyy + h / 2 + s / 6, cyy + h / 2 + s / 6}, 4);
            g.setColor(c);
            g.fillPolygon(xs, ys, 4);
            g.setColor(hi);
            g.drawLine(cxx - h + s / 5, cyy - h / 2 + 2, cxx + h - 1, cyy - h / 2 + 2);
        } else if (id == I_DIAM) {
            g.setColor(c);
            g.fillPolygon(new int[]{cxx, cxx + h, cxx, cxx - h}, new int[]{cyy - h, cyy, cyy + h, cyy}, 4);
            g.setColor(hi);
            g.fillPolygon(new int[]{cxx, cxx + h / 2, cxx, cxx - h / 2}, new int[]{cyy - h, cyy - h / 3, cyy, cyy - h / 3}, 4);
            g.setColor(dk);
            g.drawPolygon(new int[]{cxx, cxx + h, cxx, cxx - h}, new int[]{cyy - h, cyy, cyy + h, cyy}, 4);
        } else if (id == I_ARM_I || id == I_ARM_D) {
            int[] ax = {cxx - h, cxx - h / 2, cxx - h / 4, cxx + h / 4, cxx + h / 2, cxx + h, cxx + h - s / 6, cxx + h / 2, cxx + h / 2, cxx - h / 2, cxx - h / 2, cxx - h + s / 6};
            int[] ay = {cyy - h / 2, cyy - h, cyy - h + s / 6, cyy - h + s / 6, cyy - h, cyy - h / 2, cyy, cyy - h / 6, cyy + h, cyy + h, cyy - h / 6, cyy};
            g.setColor(c); g.fillPolygon(ax, ay, 12);
            g.setColor(hi); g.fillRect(cxx - h / 4, cyy - h / 4, h / 2, h / 2);
            g.setColor(dk); g.drawPolygon(ax, ay, 12);
        } else if (id == I_MEAT || id == I_COOKED || id == I_BREAD || id == I_GAPPLE) {
            g.setColor(new Color(scale(0xE8DCC8, k)));
            g.fillOval(cxx + h / 3, cyy + h / 3, h / 2 + 2, h / 2 + 2);
            g.setColor(c);
            g.fillOval(cxx - h, cyy - h / 2 - 2, s - 6, s * 2 / 3);
            g.setColor(hi);
            g.fillOval(cxx - h / 2, cyy - h / 2, h, h / 2);
        } else {                                              // уголь, железо: комочек
            g.setColor(dk);
            g.fillOval(cxx - h + 1, cyy - h + 3, s - 2, s - 4);
            g.setColor(c);
            g.fillOval(cxx - h + 2, cyy - h + 2, s - 6, s - 6);
            g.setColor(hi);
            g.fillOval(cxx - h / 2, cyy - h / 2, h / 2, h / 3);
        }
    }

    void drawSlot(Graphics2D g, int x, int y, int sz, int id, int cnt, boolean selected, boolean hover) {
        g.setColor(new Color(0, 0, 0, hover ? 185 : 135));
        g.fillRect(x, y, sz, sz);
        if (hover) { g.setColor(new Color(255, 255, 255, 40)); g.fillRect(x, y, sz, sz); }
        g.setColor(selected ? Color.WHITE : new Color(255, 255, 255, 70));
        g.setStroke(new BasicStroke(selected ? 3 : 1));
        g.drawRect(x, y, sz, sz);
        if (id <= 0) return;
        boolean have = cnt != 0;
        drawIcon(g, x + sz / 2, y + sz / 2 + 1, sz - 14, id, have);
        if (cnt >= 0) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, sz >= 44 ? 15 : 13));
            String c = String.valueOf(cnt);
            int cw = g.getFontMetrics().stringWidth(c);
            shadowText(g, c, x + sz - cw - 3, y + sz - 4, have ? Color.WHITE : new Color(0xFF8080));
        }
    }

    void drawInventoryScreen(Graphics2D g, int w, int h) {
        InvL L = invL();
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(24, 24, 32, 225));
        g.fillRect(L.px, L.py, L.pw, L.ph);
        g.setColor(new Color(255, 255, 255, 110));
        g.setStroke(new BasicStroke(2));
        g.drawRect(L.px, L.py, L.pw, L.ph);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        shadowText(g, survival ? "Инвентарь" : "Все блоки (творчество)", L.gx, L.py + 30, new Color(0xFFE890));
        if (survival) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            String[] tn = {"Кирка", "Топор", "Меч"};
            int[] tv = {pick, axe, sword};
            int tx0 = L.gx;
            for (int i = 0; i < 3; i++) {
                String t = tn[i] + ": " + TIER_NAMES[tv[i]];
                shadowText(g, t, tx0, L.py + 56, tv[i] > 0 ? new Color(0xFFE890) : new Color(0xBBBBBB));
                tx0 += g.getFontMetrics().stringWidth(t) + 18;
            }
            for (int i = 0; i < 10; i++) drawHeart(g, L.gx + 9 * (L.cell + L.gap) - L.gap - 200 + i * 20, L.py + 14, hp >= i * 2 + 2 ? 2 : hp == i * 2 + 1 ? 1 : 0);
        }
        int[] items = invItems();
        int hover = 0;
        for (int i = 0; i < L.rows * 9; i++) {
            int x = L.gx + (i % 9) * (L.cell + L.gap), y = L.gy + (i / 9) * (L.cell + L.gap);
            int id = i < items.length ? items[i] : 0;
            boolean hv = inRect(mpx, mpy, x, y, L.cell, L.cell);
            drawSlot(g, x, y, L.cell, id, id > 0 && survival ? inv[id] : -1, false, hv && id > 0);
            if (hv && id > 0) hover = id;
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        shadowText(g, "Панель быстрого доступа (цифры 1-9)", L.hx, L.hy - 8, new Color(0xDDDDDD));
        for (int j = 0; j < 9; j++) {
            int x = L.hx + j * (L.cell + L.gap);
            boolean hv = inRect(mpx, mpy, x, L.hy, L.cell, L.cell);
            int id = hot[j];
            drawSlot(g, x, L.hy, L.cell, id, id > 0 && survival ? inv[id] : -1, j == sel, hv);
            if (hv && id > 0) hover = id;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g.setColor(new Color(255, 255, 255, 200));
            g.drawString(String.valueOf(j + 1), x + 3, L.hy + 12);
        }
        if (survival) {                                       // крафт справа
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            shadowText(g, "Крафт", L.rx, L.py + 30, new Color(0xFFE890));
            int first = Math.max(0, Math.min(panelSel - L.rrows / 2, RECIPES.length - L.rrows));
            for (int j = 0; j < L.rrows; j++) {
                int i = first + j;
                Recipe r = RECIPES[i];
                boolean ok = canCraft(r);
                int ry = L.ry + 30 + j * L.rh;
                boolean hv = inRect(mpx, mpy, L.rx, ry, L.rw, L.rh - 4);
                if (i == panelSel || hv) {
                    g.setColor(new Color(255, 255, 255, hv ? 60 : 36));
                    g.fillRect(L.rx, ry, L.rw, L.rh - 4);
                }
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                shadowText(g, r.name, L.rx + 8, ry + 16, ok ? new Color(0xA8FFA8) : new Color(0xAAAAAA));
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                shadowText(g, "нужно: " + reqText(r), L.rx + 8, ry + 31, ok ? new Color(0xE0E0E0) : new Color(0x888888));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            shadowText(g, "Клик по рецепту — создать", L.rx, L.py + L.ph - 9, new Color(0xCCCCCC));
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        shadowText(g, "ЛКМ по предмету — в выбранную ячейку · Shift+ЛКМ — в свободную", L.gx, L.py + L.ph - 24, new Color(0xDDDDDD));
        shadowText(g, "ПКМ по ячейке — очистить · Q — выбросить (Shift+Q — всё) · E/Esc — закрыть", L.gx, L.py + L.ph - 9, new Color(0xDDDDDD));
        if (hover > 0) {                                      // подсказка у курсора
            String t = NAMES[hover] + (survival ? "  ×" + inv[hover] : "");
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            int tw = g.getFontMetrics().stringWidth(t);
            int bx = Math.min(w - tw - 16, mpx + 14), by = mpy + 8;
            g.setColor(new Color(10, 10, 20, 230));
            g.fillRect(bx, by, tw + 12, 24);
            g.setColor(new Color(255, 255, 255, 120));
            g.drawRect(bx, by, tw + 12, 24);
            g.setColor(Color.WHITE);
            g.drawString(t, bx + 6, by + 17);
        }
    }

    static final MobModels.Part[][] ITEM_PARTS = new MobModels.Part[NITEM][];

    static MobModels.Part[] itemParts(int id) {
        synchronized (ITEM_PARTS) {
            if (ITEM_PARTS[id] == null)
                ITEM_PARTS[id] = new MobModels.Part[]{new MobModels.Part(-0.17, 0, -0.17, 0.17, 0.34, 0.17, BASE[id], 0, 0)};
            return ITEM_PARTS[id];
        }
    }


    // ====================================================================
    //  Сундуки и печки
    // ====================================================================
    static int smeltOf(int item) {
        switch (item) {
            case I_IRON: return I_INGOT;
            case I_MEAT: return I_COOKED;
            case 15: return 3;                                    // булыжник -> камень
            case 20: return 13;                                   // глина -> кирпич
            case 8: return B_GLASS;                               // песок -> стекло
            default: return 0;
        }
    }

    static double fuelTime(int item) {
        switch (item) {
            case I_COAL: return 40;
            case 6: return 15;
            case 4: case 21: return 8;
            case I_STICK: return 4;
            default: return 0;
        }
    }

    boolean isTileBlock(int id) { return id == B_CHEST || id == B_FURN; }

    boolean tileAtTarget() { return hasTarget && isTileBlock(world.get(tx, ty, tz)); }

    /** ПКМ по сундуку, печке или кровати: открыть/воспользоваться вместо установки блока. */
    boolean useTargetBlock() {
        if (!hasTarget) return false;
        int id = world.get(tx, ty, tz);
        if (isTileBlock(id)) { openTile(tx, ty, tz); return true; }
        if (id == B_BED) { useBed(); return true; }
        if (id == B_OBSI && inv[I_FLINT] > 0 && lightPortal()) return true;
        if (id == B_TNT && (inv[I_FLINT] > 0 || !survival)) { ignite(tx, ty, tz, 3.0); return true; }
        if (id == B_TRAP || id == B_TRAP_O) { int n = id == B_TRAP ? B_TRAP_O : B_TRAP; world.set(tx, ty, tz, n); netBlock(tx, ty, tz, n); snd(n == B_TRAP_O ? "door_open" : "door_close"); return true; }
        if (id == B_DOOR_L || id == B_DOOR_LO || id == B_DOOR_U || id == B_DOOR_UO) {
            boolean low = id == B_DOOR_L || id == B_DOOR_LO, open = id == B_DOOR_L || id == B_DOOR_U;   // сейчас закрыта -> откроем
            int y0 = low ? ty : ty - 1;
            if (world.get(tx, y0, tz) != (open ? B_DOOR_L : B_DOOR_LO) || world.get(tx, y0 + 1, tz) != (open ? B_DOOR_U : B_DOOR_UO)) return true;
            int a = open ? B_DOOR_LO : B_DOOR_L, b = open ? B_DOOR_UO : B_DOOR_U;
            world.set(tx, y0, tz, a); world.set(tx, y0 + 1, tz, b);
            netBlock(tx, y0, tz, a); netBlock(tx, y0 + 1, tz, b);
            snd(open ? "door_open" : "door_close");
            return true;
        }
        return false;
    }


    /** Куда в контейнер положить предмет: номер ячейки или -1. */
    static int tileSlotFor(Tile t, int item) {
        if (t.type == B_FURN) {
            if (smeltOf(item) > 0) return t.item[0] == 0 || t.item[0] == item ? 0 : -1;
            if (fuelTime(item) > 0) return t.item[1] == 0 || t.item[1] == item ? 1 : -1;
            return -1;
        }
        for (int i = 0; i < t.item.length; i++) if (t.item[i] == item && t.cnt[i] < 999) return i;
        for (int i = 0; i < t.item.length; i++) if (t.item[i] == 0) return i;
        return -1;
    }

    /** Положить в ячейку. true, если принято целиком. */
    static boolean tilePutAt(Tile t, int slot, int item, int cnt) {
        if (slot < 0 || slot >= t.item.length || item <= 0 || item >= NITEM || cnt <= 0) return false;
        if (t.type == B_FURN) {
            if (slot == 2) return false;
            if (slot == 0 && smeltOf(item) <= 0) return false;
            if (slot == 1 && fuelTime(item) <= 0) return false;
        }
        if (t.item[slot] != 0 && t.item[slot] != item) return false;
        if (t.cnt[slot] + cnt > 999) return false;
        t.item[slot] = item;
        t.cnt[slot] += cnt;
        return true;
    }

    /** Содержимое выпадает на землю, когда контейнер сломан (хост и одиночная игра). */
    void breakTile(int x, int y, int z) {
        Tile t = world.tiles.remove(tkey(x, y, z));
        if (t == null) return;
        for (int i = 0; i < t.item.length; i++) {
            if (t.item[i] > 0 && t.cnt[i] > 0)
                spawnDrop(t.item[i], t.cnt[i], x + 0.5, y + 0.6, z + 0.5, (rnd.nextDouble() - 0.5) * 3, 3, (rnd.nextDouble() - 0.5) * 3);
        }
    }

    /** Работа печек (хост и одиночная игра). */
    void updateTiles(double dt) {
        for (Tile t : world.tiles.values()) {
            if (t.type != B_FURN) continue;
            if (world.get(t.x, t.y, t.z) != B_FURN) continue;            // чанк не загружен или блок убран
            int out = smeltOf(t.item[0]);
            boolean can = t.item[0] > 0 && t.cnt[0] > 0 && out > 0 && (t.item[2] == 0 || (t.item[2] == out && t.cnt[2] < 64));
            if (t.burn <= 0 && can && t.cnt[1] > 0 && fuelTime(t.item[1]) > 0) {
                t.burn = t.burnMax = fuelTime(t.item[1]);
                if (--t.cnt[1] <= 0) { t.cnt[1] = 0; t.item[1] = 0; }
            }
            if (t.burn > 0) {
                t.burn -= dt;
                if (can) {
                    t.prog += dt;
                    if (t.prog >= 5) {
                        t.prog = 0;
                        if (--t.cnt[0] <= 0) { t.cnt[0] = 0; t.item[0] = 0; }
                        t.item[2] = out;
                        t.cnt[2]++;
                    }
                } else t.prog = Math.max(0, t.prog - dt * 2);
            } else t.prog = Math.max(0, t.prog - dt * 2);
        }
    }

    byte[] tilePacket(Tile t) {
        Net.W w = new Net.W(Net.S_TILE).i(t.x).i(t.y).i(t.z).i(t.type).i(t.item.length);
        for (int i = 0; i < t.item.length; i++) w.i(t.item[i]).i(t.cnt[i]);
        return w.f(t.burn).f(t.burnMax).f(t.prog).done();
    }

    // ---------- экран контейнера ----------
    int uiTX, uiTY, uiTZ;
    Tile uiTile;
    double uiPoll;

    void openTile(int x, int y, int z) {
        uiTX = x; uiTY = y; uiTZ = z;
        int id = world.get(x, y, z);
        if (isClient()) {
            uiTile = new Tile(id);
            uiTile.x = x; uiTile.y = y; uiTile.z = z;
            clientConn.send(new Net.W(Net.C_TILE).b(0).i(x).i(y).i(z).i(0).i(0).i(0).done());
        } else {
            uiTile = world.getTile(x, y, z);
            if (uiTile == null) return;
        }
        panel = 3;
        snd(id == B_CHEST ? "chest" : "click");
        lmb = false;
        mining = false;
        uiPoll = 0;
        release();
        snd("click");
    }

    /** Положить из моей сумки (предметы уже списаны) в ячейку. */
    void tilePutReq(int slot, int item, int cnt) {
        if (isClient()) clientConn.send(new Net.W(Net.C_TILE).b(1).i(uiTX).i(uiTY).i(uiTZ).i(slot).i(item).i(cnt).done());
        else if (!tilePutAt(uiTile, slot, item, cnt)) addItem(item, cnt);
    }

    void tileTakeReq(int slot) {
        if (isClient()) {
            clientConn.send(new Net.W(Net.C_TILE).b(2).i(uiTX).i(uiTY).i(uiTZ).i(slot).i(0).i(0).done());
        } else if (slot >= 0 && slot < uiTile.item.length && uiTile.item[slot] > 0) {
            addItem(uiTile.item[slot], uiTile.cnt[slot]);
            uiTile.item[slot] = 0;
            uiTile.cnt[slot] = 0;
            snd("pop");
        }
    }

    static final class TL {
        int px, py, pw, ph, cell, gap, tx, ty, gx, gy, rows, hx, hy;
    }

    TL tileL() {
        TL L = new TL();
        int w = getWidth(), h = getHeight();
        L.cell = w >= 1000 && h >= 640 ? 46 : 38;
        L.gap = 4;
        int leftW = 9 * (L.cell + L.gap) - L.gap;
        L.pw = leftW + 48;
        int areaH = 3 * (L.cell + L.gap);
        int need = (invItems().length + 8) / 9;
        L.rows = Math.max(1, Math.min(3, need));
        L.ph = 50 + areaH + 30 + L.rows * (L.cell + L.gap) + 26 + L.cell + 34;
        L.px = (w - L.pw) / 2;
        L.py = Math.max(8, (h - L.ph) / 2);
        L.tx = L.px + 24;
        L.ty = L.py + 50;
        L.gx = L.tx;
        L.gy = L.ty + areaH + 30;
        L.hx = L.gx;
        L.hy = L.gy + L.rows * (L.cell + L.gap) + 26;
        return L;
    }

    int[] tileSlotXY(TL L, int i) {
        int step = L.cell + L.gap;
        if (uiTile.type == B_CHEST) return new int[]{L.tx + (i % 9) * step, L.ty + (i / 9) * step};
        if (i == 0) return new int[]{L.tx + 2 * step, L.ty};
        if (i == 1) return new int[]{L.tx + 2 * step, L.ty + 2 * step};
        return new int[]{L.tx + 6 * step, L.ty + step};
    }

    void tileClick(int mx, int my, int button) {
        if (uiTile == null) return;
        TL L = tileL();
        for (int i = 0; i < uiTile.item.length; i++) {
            int[] xy = tileSlotXY(L, i);
            if (inRect(mx, my, xy[0], xy[1], L.cell, L.cell)) {
                if (uiTile.item[i] > 0) tileTakeReq(i);
                return;
            }
        }
        int item = 0;
        int[] items = invItems();
        for (int i = 0; i < items.length && i < L.rows * 9; i++)
            if (inRect(mx, my, L.gx + (i % 9) * (L.cell + L.gap), L.gy + (i / 9) * (L.cell + L.gap), L.cell, L.cell)) item = items[i];
        for (int j = 0; j < 9; j++)
            if (inRect(mx, my, L.hx + j * (L.cell + L.gap), L.hy, L.cell, L.cell)) item = hot[j];
        if (item <= 0 || (survival && inv[item] <= 0)) return;
        int slot = tileSlotFor(uiTile, item);
        if (slot < 0) { msg = uiTile.type == B_FURN ? "Сюда это не положить" : "Сундук полон"; msgT = 1.2; return; }
        int n = button == MouseEvent.BUTTON3 ? 1 : (survival ? inv[item] : 64);
        if (survival) inv[item] -= n;
        tilePutReq(slot, item, n);
        snd("click");
    }

    void drawTileScreen(Graphics2D g, int w, int h) {
        if (uiTile == null) return;
        TL L = tileL();
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(24, 24, 32, 225));
        g.fillRect(L.px, L.py, L.pw, L.ph);
        g.setColor(new Color(255, 255, 255, 110));
        g.setStroke(new BasicStroke(2));
        g.drawRect(L.px, L.py, L.pw, L.ph);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        shadowText(g, uiTile.type == B_CHEST ? "Сундук" : "Печка", L.tx, L.py + 32, new Color(0xFFE890));
        int hover = 0;
        for (int i = 0; i < uiTile.item.length; i++) {
            int[] xy = tileSlotXY(L, i);
            boolean hv = inRect(mpx, mpy, xy[0], xy[1], L.cell, L.cell);
            int id = uiTile.item[i];
            drawSlot(g, xy[0], xy[1], L.cell, id, id > 0 ? uiTile.cnt[i] : -1, false, hv && id > 0);
            if (hv && id > 0) hover = id;
        }
        if (uiTile.type == B_FURN) {
            int step = L.cell + L.gap;
            int ax0 = L.tx + 3 * step + 6, ay = L.ty + step + L.cell / 2 - 5, aw = 3 * step - 14;
            g.setColor(new Color(60, 60, 70));
            g.fillRect(ax0, ay, aw, 10);
            g.setColor(new Color(0xF0A020));
            g.fillRect(ax0, ay, (int) (aw * Math.min(1, uiTile.prog / 5.0)), 10);
            g.setColor(new Color(255, 255, 255, 120));
            g.drawRect(ax0, ay, aw, 10);
            int fx = L.tx + 2 * step + L.cell / 2, fy = L.ty + step + L.cell / 2;          // огонёк топлива
            double fr = uiTile.burnMax > 0 ? Math.max(0, Math.min(1, uiTile.burn / uiTile.burnMax)) : 0;
            g.setColor(new Color(50, 30, 20));
            g.fillPolygon(new int[]{fx, fx + 10, fx, fx - 10}, new int[]{fy - 14, fy + 4, fy + 14, fy + 4}, 4);
            if (fr > 0) {
                g.setColor(new Color(0xFF9020));
                int hh = (int) (28 * fr);
                g.fillPolygon(new int[]{fx, fx + 9, fx, fx - 9}, new int[]{fy + 14 - hh, fy + 4, fy + 14, fy + 4}, 4);
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            shadowText(g, "Вход", L.tx + 2 * step, L.ty - 4, new Color(0xCCCCCC));
            shadowText(g, "Топливо", L.tx + 2 * step, L.ty + 2 * step - 4, new Color(0xCCCCCC));
            shadowText(g, "Готово", L.tx + 6 * step, L.ty + step - 4, new Color(0xCCCCCC));
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        shadowText(g, "Ваши вещи (клик — положить всё, ПКМ — один)", L.gx, L.gy - 8, new Color(0xDDDDDD));
        int[] items = invItems();
        for (int i = 0; i < L.rows * 9; i++) {
            int x = L.gx + (i % 9) * (L.cell + L.gap), y = L.gy + (i / 9) * (L.cell + L.gap);
            int id = i < items.length ? items[i] : 0;
            boolean hv = inRect(mpx, mpy, x, y, L.cell, L.cell);
            drawSlot(g, x, y, L.cell, id, id > 0 && survival ? inv[id] : -1, false, hv && id > 0);
            if (hv && id > 0) hover = id;
        }
        for (int j = 0; j < 9; j++) {
            int x = L.hx + j * (L.cell + L.gap);
            boolean hv = inRect(mpx, mpy, x, L.hy, L.cell, L.cell);
            int id = hot[j];
            drawSlot(g, x, L.hy, L.cell, id, id > 0 && survival ? inv[id] : -1, j == sel, hv && id > 0);
            if (hv && id > 0) hover = id;
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        shadowText(g, "Клик по ячейке контейнера — забрать · E/Esc — закрыть", L.tx, L.py + L.ph - 10, new Color(0xDDDDDD));
        if (hover > 0) {
            String t = NAMES[hover];
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            int tw = g.getFontMetrics().stringWidth(t);
            int bx = Math.min(w - tw - 16, mpx + 14), by = mpy + 8;
            g.setColor(new Color(10, 10, 20, 230));
            g.fillRect(bx, by, tw + 12, 24);
            g.setColor(new Color(255, 255, 255, 120));
            g.drawRect(bx, by, tw + 12, 24);
            g.setColor(Color.WHITE);
            g.drawString(t, bx + 6, by + 17);
        }
    }

    // ---------- посадка ----------
    void plantSeeds() {
        if (!hasTarget || tny != 1) return;
        int soil = world.get(tx, ty, tz);
        if ((soil != 1 && soil != 2) || world.get(tx, ty + 1, tz) != 0) { msg = "Семена сажают на траву или землю"; msgT = 1.5; return; }
        if (survival && inv[I_SEEDS] <= 0) return;
        world.set(tx, ty + 1, tz, B_CROP);
        if (survival) inv[I_SEEDS]--;
        netBlock(tx, ty + 1, tz, B_CROP);
        snd("put_grass", 0, 0.8);
        updateTarget();
    }


    // ====================================================================
    //  Ферма, кровать, сон и смерть
    // ====================================================================
    boolean sleeping, pvp;
    double cactusT, thunderT = 8, thunderDelay = -1, rainSndT;
    int weather;                                              // 0 ясно, 1 дождь, 2 гроза
    double weatherT = 400, rainLevel, flash;
    double sleepAcc, sleepFade, cropT;
    int sleepN, sleepTotal;
    boolean sentSleep;

    /** Рост пшеницы: раз в 5 секунд каждая грядка с небольшим шансом созревает (хост и одиночная игра). */
    void updateCrops(double dt) {
        cropT += dt;
        if (cropT < 5) return;
        cropT = 0;
        for (Map.Entry<Long, int[]> e : world.crops.entrySet()) {
            int[] p = e.getValue();
            int id = world.get(p[0], p[1], p[2]);
            if (id < 0) continue;                                        // чанк не загружен: не растёт
            if (id != B_CROP) { world.crops.remove(e.getKey()); continue; }
            int soil = world.get(p[0], p[1] - 1, p[2]);
            if (soil != 1 && soil != 2) {                                // без почвы всходы гибнут
                world.set(p[0], p[1], p[2], 0);
                netBlock(p[0], p[1], p[2], 0);
                spawnDrop(I_SEEDS, 1, p[0] + 0.5, p[1] + 0.4, p[2] + 0.5, 0, 2, 0);
                world.crops.remove(e.getKey());
                continue;
            }
            if (world.hmapAt(p[0], p[2]) > p[1]) continue;                // под крышей не растёт
            if (rnd.nextDouble() < 0.035 * (weather > 0 ? 2 : 1)) {
                world.set(p[0], p[1], p[2], B_RIPE);
                netBlock(p[0], p[1], p[2], B_RIPE);
                world.crops.remove(e.getKey());
            }
        }
    }

    void announce(String text) {
        chatAdd(text);
        if (server != null) server.broadcast(new Net.W(Net.S_CHAT).s(text).done(), null);
    }

    void useBed() {
        if (pl.x >= NT) { msg = "В Нижнем мире спать нельзя"; msgT = 2; return; }
        hasBed = true;
        bedX = tx; bedY = ty; bedZ = tz;
        if (daylight >= 0.4) {
            msg = "Точка возрождения установлена. Спать можно только ночью";
            msgT = 3;
            snd("click");
            return;
        }
        unlock("sleep");
        sleeping = true;
        sleepAcc = 0;
        sleepFade = 0;
        lmb = false;
        mining = false;
        if (isClient()) clientConn.send(new Net.W(Net.C_SLEEP).b(1).done());
        msg = "Вы легли спать (точка возрождения тоже установлена)";
        msgT = 3;
    }

    void wakeUp() {
        if (!sleeping) return;
        sleeping = false;
        if (isClient()) clientConn.send(new Net.W(Net.C_SLEEP).b(0).done());
    }

    /** Ночь пропускается, когда спят все игроки (хост и одиночная игра). */
    void sleepTick(double dt) {
        int total = 1, n = sleeping ? 1 : 0;
        if (server != null) for (Net.Conn c : server.conns) if (c.ready) { total++; if (c.sleeping) n++; }
        sleepN = n;
        sleepTotal = total;
        if (n == 0) { sleepAcc = 0; return; }
        if (daylight >= 0.5) {                                           // утро уже наступило
            sleeping = false;
            if (server != null) for (Net.Conn c : server.conns) c.sleeping = false;
            return;
        }
        sleepAcc += dt;
        if (n == total && sleepAcc > 2.5) {
            tod = 0.30;
            calcSky();
            weather = 0;
            weatherT = 240;
            sleeping = false;
            sleepAcc = 0;
            if (server != null) {
                for (Net.Conn c : server.conns) c.sleeping = false;
                server.broadcast(new Net.W(Net.S_TIME).f(tod).done(), null);
                announce("Наступило утро");
            } else { msg = "Доброе утро!"; msgT = 2.5; }
        }
    }

    /** При смерти все вещи выпадают на место гибели (инструменты остаются). */
    void spillInventory() {
        if (!survival || keepInv) return;
        List<int[]> st = new ArrayList<>();
        for (int id = 1; id < NITEM; id++) if (inv[id] > 0) st.add(new int[]{id, inv[id]});
        if (isClient()) {
            Net.W w = new Net.W(Net.C_SPILL).i(st.size());
            for (int[] x : st) w.i(x[0]).i(x[1]);
            clientConn.send(w.done());
        } else {
            for (int[] x : st) spawnDrop(x[0], x[1], pl.x, pl.y + 0.6, pl.z, rnd.nextDouble() * 4 - 2, 4 + rnd.nextDouble() * 2, rnd.nextDouble() * 4 - 2);
            if (server != null) announce(myName + " погиб");
        }
        Arrays.fill(inv, 0);
        Arrays.fill(hot, 0);
        if (!st.isEmpty()) { msg = "Вещи остались там, где вы погибли"; msgT = 4; }
    }



    // ---------- команды хоста ----------
    Net.Conn connByName(String n) {
        for (Net.Conn c : server.conns) if (c.ready && c.name.equalsIgnoreCase(n)) return c;
        return null;
    }

    void runCommand(String line) {
        String[] a = line.trim().split("\\s+");
        String cmd = a[0].toLowerCase();
        try {
            switch (cmd) {
                case "/help":
                    chatAdd("Команды: /list /time day|night|noon|0..1 /weather clear|rain|storm /tp игрок /tphere игрок");
                    chatAdd("/give предмет [число] [игрок] /heal [игрок] /kick игрок /pvp /say текст /spawn /home");
                    chatAdd("/kill [игрок] /clear /fly /seed /coords /tpc x y z /mob имя [число] /gm s|c");
                    break;
                case "/list": {
                    StringBuilder sb = new StringBuilder("Игроки: " + myName + " (хост)");
                    for (Net.Conn c : server.conns) if (c.ready) sb.append(", ").append(c.name);
                    chatAdd(sb.toString());
                    break;
                }
                case "/time": {
                    String v = a[1].toLowerCase();
                    tod = v.equals("day") ? 0.30 : v.equals("noon") ? 0.5 : v.equals("night") ? 0.80 : Double.parseDouble(v) % 1;
                    calcSky();
                    server.broadcast(new Net.W(Net.S_TIME).f(tod).done(), null);
                    announce("Время изменено хостом");
                    break;
                }
                case "/weather": {
                    String v = a[1].toLowerCase();
                    weather = v.startsWith("rain") ? 1 : v.startsWith("storm") ? 2 : 0;
                    weatherT = 300;
                    sendFlags();
                    announce("Погода изменена хостом");
                    break;
                }
                case "/pvp":
                    pvp = !pvp; sendFlags();
                    announce("PvP " + (pvp ? "включён" : "выключен") + " хостом");
                    break;
                case "/say":
                    announce("[Хост] " + line.substring(5).trim());
                    break;
                case "/gamemode": case "/gm": {
                    boolean sv = a[1].toLowerCase().startsWith("s") || a[1].equals("0");
                    survival = sv;
                    fly = false;
                    hp = MAXHP; food = 20;
                    if (!sv) initHotbar();
                    announce("Режим хоста: " + (sv ? "выживание" : "творчество"));
                    break;
                }
                case "/kill":
                    if (a.length > 1) {
                        Net.Conn c = connByName(a[1]);
                        if (c == null) { chatAdd("Нет такого игрока"); break; }
                        sendHurt(c, 1000, 0, 0);
                    } else { protT = 0; damage(1000); }
                    break;
                case "/clear":
                    Arrays.fill(inv, 0); Arrays.fill(hot, 0);
                    chatAdd("Инвентарь очищен");
                    break;
                case "/fly":
                    fly = !fly;
                    chatAdd("Полёт " + (fly ? "включён" : "выключен"));
                    break;
                case "/seed":
                    chatAdd("Зерно мира: " + gameWorld.seed);
                    break;
                case "/coords":
                    chatAdd("Вы здесь: " + (int) pl.x + " " + (int) pl.y + " " + (int) pl.z);
                    break;
                case "/tpc":
                    pl.x = Integer.parseInt(a[1]) + 0.5; pl.z = Integer.parseInt(a[3]) + 0.5;
                    pl.y = Integer.parseInt(a[2]); pl.vy = 0;
                    world.fx = pl.x; world.fz = pl.z;
                    chatAdd("Телепорт");
                    break;
                case "/mob": {
                    String q = a[1].toLowerCase();
                    String[] nm = {"зомби", "свинья", "овца", "корова", "курица", "житель"};
                    int type = -1;
                    for (int i = 0; i < nm.length; i++) if (nm[i].startsWith(q)) type = i;
                    if (type < 0) { chatAdd("Мобы: зомби, свинья, овца, корова, курица, житель"); break; }
                    int n = a.length > 2 ? Math.min(10, Integer.parseInt(a[2])) : 1;
                    for (int i = 0; i < n; i++) spawnMob(type, pl.x + rnd.nextDouble() * 4 - 2, pl.y + 0.5, pl.z + 2 + rnd.nextDouble() * 2);
                    chatAdd("Создано: " + n);
                    break;
                }
                case "/spawn":
                    pl.x = spawnX; pl.y = spawnY; pl.z = spawnZ; pl.vy = 0;
                    world.fx = pl.x; world.fz = pl.z;
                    chatAdd("Вы на точке появления");
                    break;
                case "/home":
                    if (!hasBed) { chatAdd("У вас нет кровати"); break; }
                    pl.x = bedX + 0.5; pl.y = bedY + 0.5; pl.z = bedZ + 0.5; pl.vy = 0;
                    world.fx = pl.x; world.fz = pl.z;
                    chatAdd("Вы дома");
                    break;
                case "/tp": {
                    Net.Conn c = connByName(a[1]);
                    if (c == null) { chatAdd("Нет такого игрока"); break; }
                    pl.x = c.x; pl.y = c.y + 0.2; pl.z = c.z; pl.vy = 0;
                    world.fx = pl.x; world.fz = pl.z;
                    chatAdd("Телепорт к " + c.name);
                    break;
                }
                case "/tphere": {
                    Net.Conn c = connByName(a[1]);
                    if (c == null) { chatAdd("Нет такого игрока"); break; }
                    c.send(new Net.W(Net.S_TP).f(pl.x).f(pl.y + 0.2).f(pl.z).done());
                    chatAdd(c.name + " телепортирован к вам");
                    break;
                }
                case "/kick": {
                    Net.Conn c = connByName(a[1]);
                    if (c == null) { chatAdd("Нет такого игрока"); break; }
                    announce(c.name + " исключён хостом");
                    kick(c, "Вас исключил хост");
                    break;
                }
                case "/heal": {
                    if (a.length > 1) {
                        Net.Conn c = connByName(a[1]);
                        if (c == null) { chatAdd("Нет такого игрока"); break; }
                        sendHurt(c, -20, 0, 0);
                    } else { hp = MAXHP; air = 10; }
                    chatAdd("Здоровье восстановлено");
                    break;
                }
                case "/give": {
                    String q = a[1].toLowerCase().replace('_', ' ');
                    int n = a.length > 2 ? Math.max(1, Math.min(999, Integer.parseInt(a[2]))) : 1;
                    int item = -1;
                    for (int i = 1; i < NITEM && item < 0; i++) if (NAMES[i] != null && NAMES[i].toLowerCase().equals(q)) item = i;
                    for (int i = 1; i < NITEM && item < 0; i++) if (NAMES[i] != null && NAMES[i].toLowerCase().contains(q)) item = i;
                    if (item < 0) { chatAdd("Нет такого предмета (пишите русское название, например /give алмаз 5)"); break; }
                    Net.Conn c = a.length > 3 ? connByName(a[3]) : null;
                    if (a.length > 3 && c == null) { chatAdd("Нет такого игрока"); break; }
                    if (c == null) addItem(item, n);
                    else spawnDrop(item, n, c.x, c.y + 0.8, c.z, 0, 3, 0);
                    chatAdd("Выдано: " + n + " " + NAMES[item]);
                    break;
                }
                default:
                    chatAdd("Неизвестная команда. /help — список");
            }
        } catch (RuntimeException e) {
            chatAdd("Ошибка команды. /help — список");
        }
    }


    // ====================================================================
    //  Аккаунты и скины
    // ====================================================================
    static final String CREATOR_NICK = "Qushmi";
    static final String CREATOR_VERIFY = "98375a837447d4181ae51f9cb68bcf58687bd71f4b4aa86fee464362e4179b9e";

    static String sha(String t) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(t.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    static String proofOf(String nick, String pass) { return sha("MiniCraftAcct:" + nick.toLowerCase() + ":" + pass); }

    static String verifierOf(String proof) { return sha("V:" + proof); }

    String acctProof = "", proofNick = "", skinPath = "", acctPass = "", acctMsg = "";
    int[] mySkin;
    byte[] mySkinPng;
    boolean skinSent;
    final Map<Integer, int[]> skins = new ConcurrentHashMap<>();
    final Map<Integer, byte[]> skinPngs = new ConcurrentHashMap<>();
    final Map<String, int[]> defSkins = new HashMap<>();
    final Map<String, String> accounts = new HashMap<>();

    boolean selfCreator() {
        return myName.equalsIgnoreCase(CREATOR_NICK) && proofNick.equalsIgnoreCase(myName) && !acctProof.isEmpty() && verifierOf(acctProof).equals(CREATOR_VERIFY);
    }

    static String tagName(String name, boolean creator) { return creator ? "\u0001" + name + "\u0002" : name; }

    static File accountsFile() { return new File(dataDir(), "accounts.properties"); }

    void loadAccounts() {
        try {
            File f = accountsFile();
            if (!f.exists()) return;
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(f)) { p.load(in); }
            for (String k : p.stringPropertyNames()) accounts.put(k, p.getProperty(k));
        } catch (Exception ignored) { }
    }

    void saveAccounts() {
        try {
            Properties p = new Properties();
            p.putAll(accounts);
            try (FileOutputStream out = new FileOutputStream(accountsFile())) { p.store(out, "MiniCraft accounts (nick -> password verifier)"); }
        } catch (Exception ignored) { }
    }

    /** Проверка входа на хосте: null - можно, иначе причина отказа. */
    String verifyLogin(String nick, String proof) {
        if (proof.length() > 100) proof = "";
        String v = proof.isEmpty() ? "" : verifierOf(proof);
        if (nick.equalsIgnoreCase(CREATOR_NICK))
            return v.equals(CREATOR_VERIFY) ? null : "Ник " + CREATOR_NICK + " принадлежит создателю игры. Выберите другой ник";
        String key = nick.toLowerCase();
        String st = accounts.get(key);
        if (st != null) return st.equals(v) ? null : (v.isEmpty() ? "Ник «" + nick + "» защищён паролем (меню → Аккаунт)" : "Неверный пароль для ника «" + nick + "»");
        if (!v.isEmpty()) { accounts.put(key, v); saveAccounts(); }
        return null;
    }

    void saveAccount() {
        myName = cleanName(myName, 0);
        if (!acctPass.isEmpty()) { acctProof = proofOf(myName, acctPass); proofNick = myName; acctMsg = "Аккаунт сохранён. "; }
        else if (!proofNick.equalsIgnoreCase(myName)) { acctProof = ""; proofNick = ""; acctMsg = "Пароль не задан: ник без защиты. "; }
        else acctMsg = "Сохранено. ";
        acctPass = "";
        String sm = loadMySkin();
        acctMsg += sm;
        saveConfig();
    }

    String loadMySkin() {
        mySkin = null;
        mySkinPng = null;
        skinSent = false;
        String pth = skinPath.trim().replace("\"", "");
        if (pth.isEmpty()) return "Скин: автоматический";
        int[] sk = loadSkin(new File(pth));
        if (sk == null) return "Не удалось прочитать скин (нужен PNG 64x64 или 64x32)";
        mySkin = sk;
        mySkinPng = skinPng(sk);
        return "Скин загружен";
    }

    static int[] loadSkin(File f) {
        try { return skinFromImage(javax.imageio.ImageIO.read(f)); } catch (Exception e) { return null; }
    }

    static int[] decodeSkin(byte[] b) {
        try { return skinFromImage(javax.imageio.ImageIO.read(new ByteArrayInputStream(b))); } catch (Exception e) { return null; }
    }

    static int[] skinFromImage(BufferedImage bi) {
        if (bi == null || bi.getWidth() != 64 || (bi.getHeight() != 64 && bi.getHeight() != 32)) return null;
        int[] px = new int[4096];
        int hgt = bi.getHeight();
        for (int y = 0; y < hgt; y++) for (int x = 0; x < 64; x++) px[y * 64 + x] = bi.getRGB(x, y);
        if (hgt == 32) {                                                        // старый формат: левые конечности копируем с правых
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                px[(48 + y) * 64 + 32 + x] = px[(16 + y) * 64 + 40 + x];
                px[(48 + y) * 64 + 16 + x] = px[(16 + y) * 64 + x];
            }
        }
        if (hgt == 32 || !bi.getColorModel().hasAlpha()) {                       // без прозрачности второй слой не используем
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                boolean ov = (y < 16 && x >= 32) || (y >= 32 && y < 48) || (y >= 48 && (x >= 48 || x < 16));
                if (ov) px[y * 64 + x] &= 0x00FFFFFF;
                else px[y * 64 + x] |= 0xFF000000;
            }
        }
        return px;
    }

    static byte[] skinPng(int[] px) {
        try {
            BufferedImage bi = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            bi.setRGB(0, 0, 64, 64, px, 0, 64);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bi, "png", bo);
            return bo.toByteArray();
        } catch (Exception e) { return null; }
    }

    // раскладка частей тела в скине 64x64: {u, v, ширина, высота, глубина, u2, v2} (u2,v2 - второй слой)
    static final int[][] SKIN_PARTS = {
            {0, 0, 8, 8, 8, 32, 0}, {16, 16, 8, 12, 4, 16, 32}, {40, 16, 4, 12, 4, 40, 32},
            {32, 48, 4, 12, 4, 48, 48}, {0, 16, 4, 12, 4, 0, 32}, {16, 48, 4, 12, 4, 0, 48}};

    /** Цвет точки на грани части тела. f* - положение в коробке 0..1, ax/sg - грань (ось и знак нормали). */
    static int skinAt(int[] sk, int part, double fx, double fy, double fz, int ax, int sg) {
        int[] P = SKIN_PARTS[part];
        int u = P[0], v = P[1], w = P[2], h = P[3], d = P[4];
        int face = ax == 1 ? (sg > 0 ? 0 : 1) : ax == 0 ? (sg < 0 ? 2 : 4) : (sg > 0 ? 3 : 5);
        int rx, ry, rw, rh;
        double tx, ty;
        switch (face) {
            case 0: rx = u + d; ry = v; rw = w; rh = d; tx = fx; ty = 1 - fz; break;
            case 1: rx = u + d + w; ry = v; rw = w; rh = d; tx = fx; ty = fz; break;
            case 2: rx = u; ry = v + d; rw = d; rh = h; tx = fz; ty = 1 - fy; break;
            case 3: rx = u + d; ry = v + d; rw = w; rh = h; tx = fx; ty = 1 - fy; break;
            case 4: rx = u + d + w; ry = v + d; rw = d; rh = h; tx = 1 - fz; ty = 1 - fy; break;
            default: rx = u + d + w + d; ry = v + d; rw = w; rh = h; tx = 1 - fx; ty = 1 - fy; break;
        }
        int px = rx + Math.min(rw - 1, (int) (tx * rw)), py = ry + Math.min(rh - 1, (int) (ty * rh));
        int c = sk[py * 64 + px];
        int o = sk[(py - v + P[6]) * 64 + (px - u + P[5])];
        if ((o >>> 24) >= 128) c = o;
        return c & 0xFFFFFF;
    }

    int[] skinFor(RPlayer r) {
        int[] s = skins.get(r.id);
        if (s != null) return s;
        return defaultSkin(r.name);
    }

    int[] defaultSkin(String name) {
        int[] s = defSkins.get(name);
        if (s == null) { s = genSkin(name); if (defSkins.size() > 64) defSkins.clear(); defSkins.put(name, s); }
        return s;
    }

    static void fillRect(int[] sk, int x, int y, int w, int h, int c) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) if (xx >= 0 && xx < 64 && yy >= 0 && yy < 64) sk[yy * 64 + xx] = 0xFF000000 | c;
    }

    static void fillBox(int[] sk, int u, int v, int w, int h, int d, int c) {
        fillRect(sk, u + d, v, w, d, c); fillRect(sk, u + d + w, v, w, d, c);
        fillRect(sk, u, v + d, d, h, c); fillRect(sk, u + d, v + d, w, h, c);
        fillRect(sk, u + d + w, v + d, d, h, c); fillRect(sk, u + d + w + d, v + d, w, h, c);
    }

    /** Красивый скин по нику: лицо с глазами и бровями, причёска, рубашка с узором, штаны, ботинки. */
    static int[] genSkin(String name) {
        java.util.Random r = new java.util.Random(name.toLowerCase().hashCode() * 2654435761L + 12345);
        int[] skinT = {0xF1C9A5, 0xE0AC87, 0xC68C64, 0x9C6A47, 0x6F4A33};
        int[] hairC = {0x2B1B10, 0x5A3A1E, 0x8A5A2B, 0xD8B456, 0x1A1A1E, 0xA33A22, 0xC8C8D0, 0x3A5AA8};
        int[] shirtC = {0x2E9E4B, 0xC8442E, 0x3C6FD0, 0xD8B22C, 0x8E44AD, 0x16A5A5, 0xE67E22, 0xD35D8F};
        int[] pantsC = {0x3B3B8C, 0x2F3A4F, 0x4A3A2A, 0x3A3A3A, 0x2A5A3A};
        int[] eyeC = {0x3A7BD8, 0x3E8E41, 0x6B4423, 0x4A4A55};
        int skin = skinT[r.nextInt(skinT.length)], hair = hairC[r.nextInt(hairC.length)], shirt = shirtC[r.nextInt(shirtC.length)];
        int pants = pantsC[r.nextInt(pantsC.length)], eye = eyeC[r.nextInt(eyeC.length)];
        int style = r.nextInt(3), hstyle = r.nextInt(3);
        int[] sk = new int[4096];
        // голова
        fillBox(sk, 0, 0, 8, 8, 8, skin);
        fillRect(sk, 8, 0, 8, 8, hair);                                  // верх
        fillRect(sk, 24, 8, 8, 8, hair);                                 // затылок
        int sideRows = hstyle == 2 ? 6 : 3;
        fillRect(sk, 0, 8, 8, sideRows, hair);
        fillRect(sk, 16, 8, 8, sideRows, hair);
        if (hstyle == 2) fillRect(sk, 24, 8, 8, 8, hair);
        fillRect(sk, 8, 8, 8, 1, hair);                                  // чёлка
        if (hstyle == 0) { fillRect(sk, 8, 9, 2, 1, hair); fillRect(sk, 14, 9, 2, 1, hair); }
        else if (hstyle == 1) { fillRect(sk, 8, 9, 4, 1, hair); fillRect(sk, 8, 10, 1, 1, hair); }
        else { fillRect(sk, 8, 9, 1, 3, hair); fillRect(sk, 15, 9, 1, 3, hair); fillRect(sk, 9, 9, 6, 1, hair); }
        fillRect(sk, 9, 12, 2, 1, 0xFFFFFF); fillRect(sk, 13, 12, 2, 1, 0xFFFFFF);          // белки глаз
        fillRect(sk, 10, 12, 1, 1, eye); fillRect(sk, 13, 12, 1, 1, eye);                  // зрачки
        fillRect(sk, 9, 11, 2, 1, hair); fillRect(sk, 13, 11, 2, 1, hair);                  // брови
        fillRect(sk, 11, 13, 2, 1, shade(skin, 0.86));                                      // нос
        fillRect(sk, 11, 14, 2, 1, 0xA84848);                                               // рот
        // туловище и руки
        fillBox(sk, 16, 16, 8, 12, 4, shirt);
        fillRect(sk, 22, 20, 4, 2, skin);                                                   // вырез воротника
        fillRect(sk, 20, 31, 8, 1, shade(shirt, 0.55));                                     // пояс
        if (style == 1) fillRect(sk, 20, 24, 8, 2, shade(shirt, 1.3));
        else if (style == 2) { fillRect(sk, 24, 23, 2, 2, shade(shirt, 1.4)); fillRect(sk, 25, 22, 1, 1, shade(shirt, 1.4)); }
        for (int k = 0; k < 2; k++) {
            int u = k == 0 ? 40 : 32, v = k == 0 ? 16 : 48;
            fillBox(sk, u, v, 4, 12, 4, shirt);
            fillRect(sk, u, v + 9, 16, 7, skin);                                            // предплечья - кожа
            fillRect(sk, u + 4, v, 4, 4, shirt); fillRect(sk, u + 8, v, 4, 4, skin);       // верх / низ руки
        }
        // ноги
        for (int k = 0; k < 2; k++) {
            int u = k == 0 ? 0 : 16, v = k == 0 ? 16 : 48;
            fillBox(sk, u, v, 4, 12, 4, pants);
            fillRect(sk, u, v + 13, 16, 3, shade(pants, 0.45));                             // ботинки
            fillRect(sk, u + 4, v, 8, 4, pants);
        }
        // лёгкий шум для фактуры
        for (int i = 0; i < 4096; i++) {
            if ((sk[i] >>> 24) == 0) continue;
            double k = 0.94 + r.nextDouble() * 0.12;
            sk[i] = 0xFF000000 | shade(sk[i] & 0xFFFFFF, k);
        }
        return sk;
    }

    static String stars(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append('*');
        return sb.toString();
    }

    static int shade(int c, double k) {
        int rr = Math.min(255, (int) (((c >> 16) & 255) * k)), gg = Math.min(255, (int) (((c >> 8) & 255) * k)), bb = Math.min(255, (int) ((c & 255) * k));
        return (rr << 16) | (gg << 8) | bb;
    }

    /** Кукла для предпросмотра скина (вид спереди). */
    void drawPaperdoll(Graphics2D g, int[] sk, int x0, int y0, int sc) {
        int[][] regs = {{8, 8, 8, 8, 4, 0}, {20, 20, 8, 12, 4, 8}, {44, 20, 4, 12, 0, 8}, {36, 52, 4, 12, 12, 8}, {4, 20, 4, 12, 4, 20}, {20, 52, 4, 12, 8, 20}};
        for (int[] rg : regs) {
            for (int yy = 0; yy < rg[3]; yy++) for (int xx = 0; xx < rg[2]; xx++) {
                int c = sk[(rg[1] + yy) * 64 + rg[0] + xx];
                int ov = -1;
                if (rg[0] == 8 && rg[1] == 8) ov = sk[yy * 64 + 40 + xx];
                else if (rg[0] == 20 && rg[1] == 20) ov = sk[(yy + 12) * 64 + 20 + xx];
                if (ov != -1 && (ov >>> 24) >= 128) c = ov;
                g.setColor(new Color(c & 0xFFFFFF));
                g.fillRect(x0 + (rg[4] + xx) * sc, y0 + (rg[5] + yy) * sc, sc, sc);
            }
        }
    }

    void paintAccount(Graphics2D g, int w, int h) {
        int fy = fieldY0(), bw = Math.min(480, w - 40), bx = (w - bw) / 2;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        centerText(g, "Аккаунт", w / 2, fy - 4, Color.WHITE);
        String[] lab = {"Ник (имя в игре)", "Пароль (защищает ваш ник на серверах; хранится только хеш)", "Файл скина PNG 64x64 (Ctrl+V — вставить путь)"};
        String[] val = {myName, stars(acctPass.length()), skinPath};
        for (int i = 0; i < 3; i++) {
            int y = fy + i * 70;
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            shadowText(g, lab[i], bx, y + 14, new Color(0xE8E8E8));
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(bx, y + 20, bw, 36);
            g.setColor(field == i ? new Color(0xFFE890) : new Color(0x888888));
            g.drawRect(bx, y + 20, bw, 36);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
            String t = val[i];
            if (t.length() > 34) t = "…" + t.substring(t.length() - 33);
            t += field == i && ((int) (animT * 2) % 2 == 0) ? "_" : "";
            g.setColor(i == 0 && selfCreator() ? new Color(0xFFE040) : Color.WHITE);
            g.drawString(t, bx + 10, y + 44);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        String st = acctMsg;
        if (st.isEmpty()) {
            if (selfCreator()) st = "Создатель игры подтверждён — ваш ник жёлтый";
            else if (myName.equalsIgnoreCase(CREATOR_NICK)) st = "Ник " + CREATOR_NICK + " зарезервирован: нужен пароль создателя";
            else st = proofNick.equalsIgnoreCase(myName) && !acctProof.isEmpty() ? "Ник защищён паролем" : "Ник без пароля";
        }
        centerText(g, st, w / 2, fy + 3 * 70 + 6, new Color(0xFFF0A0));
        int[] sk = mySkin != null ? mySkin : defaultSkin(myName);
        int sc = Math.max(3, Math.min(7, (w - bw) / 2 / 18));
        if (bx > 16 * sc + 20) drawPaperdoll(g, sk, bx + bw + 24 > w - 16 * sc ? w - 16 * sc - 10 : bx + bw + 24, fy + 10, sc);
    }

    // ---------- погода ----------
    void updateWeather(double dt) {
        if (!isClient()) {
            weatherT -= dt;
            if (weatherT <= 0) {
                int old = weather;
                if (weather == 0) { weather = rnd.nextDouble() < 0.6 ? 1 : 0; weatherT = weather == 1 ? 120 + rnd.nextDouble() * 180 : 240 + rnd.nextDouble() * 300; }
                else if (weather == 1) { weather = rnd.nextDouble() < 0.3 ? 2 : 0; weatherT = weather == 2 ? 60 + rnd.nextDouble() * 80 : 300 + rnd.nextDouble() * 300; }
                else { weather = 0; weatherT = 300 + rnd.nextDouble() * 300; }
                if (weather != old && server != null) sendFlags();
            }
        }
        double target = weather == 2 ? 1 : weather == 1 ? 0.75 : 0;
        rainLevel += Math.max(-dt * 0.2, Math.min(dt * 0.2, target - rainLevel));
        flash = Math.max(0, flash - dt * 1.8);
        if (weather == 2) {
            thunderT -= dt;
            if (thunderT <= 0) { flash = 1; thunderDelay = 0.3 + rnd.nextDouble() * 1.5; thunderT = 6 + rnd.nextDouble() * 14; }
        }
        if (thunderDelay >= 0) { thunderDelay -= dt; if (thunderDelay < 0) snd("thunder", 0, 1); }
        if (rainLevel > 0.3 && state == State.PLAYING && biomeHere() != 1) {
            rainSndT -= dt;
            if (rainSndT <= 0) { rainSndT = 0.3; snd("rain", 0, (exposed() ? 0.45 : 0.15) * rainLevel); }
        }
    }

    int biomeHere() { return world.biome((int) Math.floor(pl.x), (int) Math.floor(pl.z)); }

    boolean exposed() { return world.hmapAt((int) Math.floor(pl.x), (int) Math.floor(pl.z)) <= pl.y + 1; }

    void sendFlags() {
        if (server != null) server.broadcast(new Net.W(Net.S_FLAGS).b(weather).b(pvp ? 1 : 0).done(), null);
    }

    void paintWeather(Graphics2D g, int w, int h) {
        if (fxWeather && rainLevel > 0.05 && state != State.DEAD && exposed() && biomeHere() != 1) {
            boolean snowy = biomeHere() == 2;
            long t = System.nanoTime() / 1000000L;
            Random r = new Random(t / 60);
            int n = (int) (rainLevel * (snowy ? 140 : 220));
            if (snowy) {
                g.setColor(new Color(255, 255, 255, 200));
                for (int i = 0; i < n; i++) {
                    int x = (int) ((new Random(i * 7919L).nextDouble() * w) + Math.sin(t / 700.0 + i) * 20) % w;
                    int y = (int) (((new Random(i * 31L).nextDouble() * h) + t * (0.05 + (i % 5) * 0.012)) % h);
                    g.fillRect(x, y, 3, 3);
                }
            } else {
                g.setColor(new Color(170, 190, 230, 150));
                for (int i = 0; i < n; i++) {
                    int x = r.nextInt(w), y = r.nextInt(h);
                    g.drawLine(x, y, x - 4, y + 16);
                }
            }
        }
        if (flash > 0) { g.setColor(new Color(255, 255, 255, (int) (flash * 170))); g.fillRect(0, 0, w, h); }
    }

    // ---------- крафт и торговля ----------
    int panel, panelSel;                                      // 0 нет, 1 крафт, 2 торговля

    Recipe[] curList() { return panel == 2 ? TRADES : RECIPES; }

    int tierOf(int tool) { return tool == 1 ? pick : tool == 2 ? axe : sword; }

    boolean canCraft(Recipe r) {
        for (int i = 0; i < r.in.length; i += 2) if (inv[r.in[i]] < r.in[i + 1]) return false;
        return r.tool == 0 || tierOf(r.tool) < r.tier;
    }

    void doCraft(int i) {
        Recipe r = curList()[i];
        if (!canCraft(r)) { msg = r.tool > 0 && tierOf(r.tool) >= r.tier ? "У вас уже есть лучше" : "Не хватает материалов"; msgT = 1.5; return; }
        for (int k = 0; k < r.in.length; k += 2) inv[r.in[k]] -= r.in[k + 1];
        if (r.tool == 0) addItem(r.out, r.cnt);
        else if (r.tool == 1) pick = r.tier;
        else if (r.tool == 2) axe = r.tier;
        else sword = r.tier;
        msg = "Получено: " + r.name;
        msgT = 1.5;
        snd(panel == 2 ? "pop" : "craft");
    }

    static int foodValue(int id) { return id == I_GAPPLE ? 20 : id == I_MEAT ? 6 : id == I_BREAD ? 8 : id == I_COOKED ? 12 : 0; }

    void eatItem(int id) {
        int v = foodValue(id);
        if (!survival || v == 0 || inv[id] <= 0) return;
        if (id == I_GAPPLE) {                                  // золотой хлеб: всегда съедобен, лечит всё
            if (hp >= MAXHP && food >= 20) return;
            hp = MAXHP; food = 20; protT = Math.max(protT, 2);
            inv[id]--; msg = "Золотой хлеб: здоровье и сытость полные!"; msgT = 2; snd("eat");
            return;
        }
        if (hungerOn) {
            if (food >= 20) return;
            food = Math.min(20, food + v);
            msg = "Вкусно! Сытость +" + (v / 2);
        } else {
            if (hp >= MAXHP) return;
            hp = Math.min(MAXHP, hp + v);
            msg = "Вкусно! +" + (v / 2) + " сердец";
        }
        inv[id]--;
        msgT = 1.5;
        snd("eat");
    }

    /** Съесть то, что в руке, а если это не еда - лучшее из того, что есть. */
    void eat() {
        if (foodValue(hot[sel]) > 0 && inv[hot[sel]] > 0) { eatItem(hot[sel]); return; }
        for (int id : new int[]{I_COOKED, I_BREAD, I_MEAT}) if (inv[id] > 0) { eatItem(id); return; }
    }

    static String reqText(Recipe r) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < r.in.length; k += 2) {
            if (k > 0) sb.append(", ");
            sb.append(r.in[k + 1]).append(" ").append(NAMES[r.in[k]]);
        }
        return sb.toString();
    }

    static double wrapAng(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    boolean undergroundAt(double x, double y, double z) {
        return world.hmapAt((int) Math.floor(x), (int) Math.floor(z)) - y > 4;
    }

    int countType(int type) {
        int n = 0;
        for (Mob m : mobs) if (m.type == type) n++;
        return n;
    }

    int countPassive() {
        int n = 0;
        for (Mob m : mobs) if (m.type != MobModels.ZOMBIE && m.type != MobModels.VILLAGER) n++;
        return n;
    }

    void updateMobs(double dt) {
        spawnT -= dt;
        if (spawnT <= 0) {
            spawnT = 1.5 + rnd.nextDouble() * 1.5;
            int players = 1 + (server != null ? server.conns.size() : 0);
            int pick = rnd.nextInt(players);                           // кого из игроков выбрать центром появления
            scx = pl.x; scy = pl.y; scz = pl.z;
            if (pick > 0 && server != null) {
                Net.Conn c = server.conns.get(Math.min(pick - 1, server.conns.size() - 1));
                if (c.ready) { scx = c.x; scy = c.y; scz = c.z; }
            }
            if (survival) trySpawnHostile();
            if (animalsOn && countPassive() < animalCap + 3 * (players - 1)) trySpawnAnimal(14, 34);
            trySpawnVillager();
        }
        for (Iterator<Mob> it = mobs.iterator(); it.hasNext(); ) {
            Mob m = it.next();
            Target tg = nearestPlayer(m.x, m.z);
            double dx = tg.x - m.x, dz = tg.z - m.z;
            double d = tg.d;
            double dHost = Math.hypot(pl.x - m.x, pl.z - m.z);
            boolean under = undergroundAt(m.x, m.y, m.z);
            if (d > 90 || (m.type != MobModels.VILLAGER && d > 70)
                    || (m.hostile() && !under && daylight > 0.6 && rnd.nextDouble() < dt * 0.25)) { it.remove(); continue; }
            m.hurt -= dt;
            m.atk -= dt;
            m.sndT -= dt;
            if (m.sndT <= 0) {
                m.sndT = m.hostile() ? 5 + rnd.nextDouble() * 6 : 8 + rnd.nextDouble() * 14;
                if (dHost < 26) snd(mobSound(m.type), dHost, 0.8);
            }
            double ux = d > 0.01 ? dx / d : 0, uz = d > 0.01 ? dz / d : 0;
            double sp = 0, wx = 0, wz = 0;        // желаемая скорость и направление
            boolean face = false;
            if (m.hostile()) {
                if (d < 28) { sp = 2.6; wx = ux; wz = uz; face = true; }
            } else {
                m.aiT -= dt;
                if (m.fleeT > 0) {
                    m.fleeT -= dt;
                    sp = 4.2; wx = -ux; wz = -uz;
                } else {
                    if (m.aiT <= 0) {
                        m.moving = !m.moving;
                        m.aiT = m.moving ? 2 + rnd.nextDouble() * 3 : 1 + rnd.nextDouble() * 3;
                        if (m.moving) m.dirA = rnd.nextDouble() * Math.PI * 2;
                    }
                    if (m.type == MobModels.VILLAGER) {                    // жители не уходят далеко от центра деревни
                        double hx = m.homeX - m.x, hz = m.homeZ - m.z;
                        if (Math.hypot(hx, hz) > 22) { m.dirA = Math.atan2(hx, hz); m.moving = true; }
                    }
                    if (m.moving) { sp = m.type == MobModels.CHICKEN ? 1.0 : 1.3; wx = Math.sin(m.dirA); wz = Math.cos(m.dirA); }
                    if (m.blocked && m.moving && !m.onGround) m.dirA += 1.5;
                }
            }
            boolean mw = world.isWater(m.x, m.y + 0.3, m.z);
            m.kx -= m.kx * Math.min(1, dt * 6);
            m.kz -= m.kz * Math.min(1, dt * 6);
            m.vx = wx * sp + m.kx;
            m.vz = wz * sp + m.kz;
            if (mw) m.vy = 2.0;                                           // всплывают
            else {
                m.vy = Math.max(-50, m.vy - 28 * dt);
                if (m.type == MobModels.CHICKEN && m.vy < -2.5) m.vy = -2.5;   // курица планирует
            }
            if (m.blocked && m.onGround && sp > 0) m.vy = 8.5;
            m.step(dt);
            // поворот и анимация ног
            if (sp > 0.05) {
                double target = face ? Math.atan2(ux, uz) : Math.atan2(wx, wz);
                double diff = wrapAng(target - m.ang);
                m.ang += Math.max(-9 * dt, Math.min(9 * dt, diff));
                m.phase += dt * sp * 3.4;
            }
            m.swing += ((sp > 0.05 ? 1 : 0) - m.swing) * Math.min(1, dt * 10);
            // зомби бьёт
            if (m.hostile() && d < 0.95 && Math.abs(tg.y - m.y) < 1.6 && m.atk <= 0) {
                if (tg.conn == null) {
                    damage(zdmg());
                    kbx = ux * 7;
                    kbz = uz * 7;
                    pl.vy = Math.max(pl.vy, 4);
                } else sendHurt(tg.conn, zdmg(), ux * 7, uz * 7);
                m.atk = 1.0;
            }
        }
    }

    void spawnMob(int type, double x, double y, double z) {
        Mob m = new Mob(type);
        m.x = x; m.y = y; m.z = z;
        m.homeX = x; m.homeZ = z;
        mobs.add(m);
    }

    void trySpawnHostile() {
        int zc = countType(MobModels.ZOMBIE);
        boolean pUnder = undergroundAt(scx, scy, scz);
        if (diff == 0) { mobs.removeIf(Mob::hostile); return; }
        if (zc >= (diff == 1 ? 3 : diff == 2 ? 5 : 8) - (pUnder ? 1 : 0)) return;
        for (int attempt = 0; attempt < 6; attempt++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 12 + rnd.nextDouble() * 14;
            int x = (int) Math.floor(scx + Math.cos(a) * r), z = (int) Math.floor(scz + Math.sin(a) * r);
            if (world.chunk(x >> 4, z >> 4) == null) continue;
            int y;
            if (pUnder) y = (int) Math.floor(scy) - 4 + rnd.nextInt(9);
            else if (daylight < 0.35) y = world.surfaceY(x, z) + 1;
            else continue;
            if (y < 2 || y >= WY - 3) continue;
            if (world.get(x, y, z) != 0 || world.get(x, y + 1, z) != 0 || !world.solid(x, y - 1, z)) continue;
            if (world.get(x, y - 1, z) == 5) continue;
            if (!undergroundAt(x, y, z) && daylight >= 0.35) continue;
            if (!Villages.around(world, x, z, 34).isEmpty() && daylight >= 0.35) continue;
            spawnMob(MobModels.ZOMBIE, x + 0.5, y, z + 0.5);
            return;
        }
    }

    void trySpawnAnimal(double rmin, double rmax) {
        double a = rnd.nextDouble() * Math.PI * 2, r = rmin + rnd.nextDouble() * (rmax - rmin);
        int x = (int) Math.floor(scx + Math.cos(a) * r), z = (int) Math.floor(scz + Math.sin(a) * r);
        if (world.chunk(x >> 4, z >> 4) == null) return;
        int y = world.surfaceY(x, z);
        if (y < 1 || y >= WY - 3 || !(world.get(x, y, z) == 1 || world.get(x, y, z) == 9)) return;        // трава и снег
        if (world.get(x, y + 1, z) != 0 || world.get(x, y + 2, z) != 0) return;
        int[] kinds = {MobModels.PIG, MobModels.SHEEP, MobModels.COW, MobModels.CHICKEN, MobModels.SHEEP, MobModels.COW};
        spawnMob(kinds[rnd.nextInt(kinds.length)], x + 0.5, y + 1, z + 0.5);
    }

    void trySpawnVillager() {
        for (Villages.Village v : Villages.around(world, scx, scz, 80)) {
            int have = 0;
            for (Mob m : mobs) if (m.type == MobModels.VILLAGER && m.village == v.id) have++;
            int want = Math.min(6, v.houses.size());
            if (have >= want) continue;
            Villages.House h = v.houses.get(rnd.nextInt(v.houses.size()));
            int x = h.outX(2), z = h.outZ(2);
            if (world.chunk(x >> 4, z >> 4) == null) continue;
            int y = v.y0 + 1;
            if (world.get(x, y, z) != 0 || world.get(x, y + 1, z) != 0) continue;
            Mob m = new Mob(MobModels.VILLAGER);
            m.x = x + 0.5; m.y = y; m.z = z + 0.5;
            m.village = v.id;
            m.homeX = v.cx;
            m.homeZ = v.cz;
            mobs.add(m);
            return;
        }
    }

    // ====================================================================
    //  Небо и время суток
    // ====================================================================
    void calcSky() {
        double th = 2 * Math.PI * tod - Math.PI / 2;
        double s = Math.sin(th);
        double l = Math.sqrt(1 + s * s + 0.35 * 0.35);
        sunX = Math.cos(th) / l;
        sunY = s / l;
        sunZ = 0.35 / l;
        double k = clamp01((s + 0.15) / 0.5);
        daylight = k * k * (3 - 2 * k);
        skyTopC = mix(0x040814, 0x4A80D8, daylight);
        int hor = mix(0x0A1226, 0xBFD8F5, daylight);
        double tint = Math.max(0, 1 - Math.abs(daylight - 0.4) / 0.4) * 0.5;
        skyHorC = mix(hor, 0xF08A50, tint);
        if (rainLevel > 0.001) {
            int gr = mix(0x0C0E12, 0x80878F, daylight);
            skyTopC = mix(skyTopC, gr, rainLevel * 0.8);
            skyHorC = mix(skyHorC, mix(gr, 0xA0A6AD, 0.3), rainLevel * 0.85);
        }
        if (state != State.MENU && state != State.SPLASH && pl.x >= NT) { skyTopC = 0x2A0606; skyHorC = 0x3E0C0C; }
    }

    int skyColor(double dx, double dy, double dz) {
        double k = Math.sqrt(Math.max(0, dy));
        int c = mix(skyHorC, skyTopC, Math.min(1, k * 1.2));
        double sd = dx * sunX + dy * sunY + dz * sunZ;
        if (sd > 0.9985) return 0xFFF4B8;
        if (-sd > 0.9990) return 0xE6E8F0;
        if (sd > 0.96) c = mix(c, 0xFFE9A0, (sd - 0.96) / 0.04 * 0.5 * daylight);
        if (daylight < 0.5 && dy > 0.02) {
            int h = ih((int) (dx * 500), (int) (dy * 500), (int) (dz * 500));
            if ((h & 511) == 0) c = mix(c, 0xFFFFFF, 0.8 * (1 - daylight * 2));
        }
        return c;
    }

    // ====================================================================
    //  Рендер
    // ====================================================================
    double animT;
    volatile List<Object[]> tagList = new ArrayList<>();            // имя, x, y (доли экрана), расстояние



    /** Освещённость клетки: небо ослабевает под крышей, в темноте работает слабый налобный свет. */
    static double skyLit(World w, double amb, int x, int y, int z, double dist) {
        if (x >= NT) return 0.42;
        double depth = w.hmapAt(x, z) - y;
        double sf = clamp01(1 - (depth - 1) / 4.0);
        if (sf >= 1) return amb;
        return sf * amb + (1 - sf) * (0.10 + Math.max(0, 1 - dist / 9) * 0.55);
    }

    /** Яркость от светящихся блоков: уровень 15 у источника, спадает на 1 за блок. */
    static double blockLit(World w, int x, int y, int z) {
        int b = w.blightAt(x, y, z);
        if (b <= 0) return 0;
        return 0.06 + 1.1 * Math.pow(b / 15.0, 1.5);
    }

    static double lightAt(World w, double amb, int x, int y, int z, double dist) {
        return Math.max(skyLit(w, amb, x, y, z, dist), blockLit(w, x, y, z));
    }

    /** Тёплый оттенок света от светокамня. */
    static int warmTint(int c, double k) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * (1 + 0.14 * k));
        int g = (int) (((c >> 8) & 255) * (1 - 0.04 * k));
        int bb = (int) ((c & 255) * (1 - 0.30 * k));
        return (r << 16) | (g << 8) | bb;
    }

    void render() {
        final World w = world;
        final boolean mc = state == State.SPLASH || state == State.MENU;
        final double ex, ey, ez, yw, pt;
        if (mc) {
            ex = orbitX; ey = orbitY; ez = orbitZ; yw = orbitYaw; pt = -0.28;
        } else {
            ex = pl.x; ey = pl.y + 1.62; ez = pl.z; yw = yaw; pt = pitch;
        }
        calcSky();
        final double maxd = w.R * CS - 8;
        final double amb = Math.min(1.15, 0.26 + 0.74 * daylight * (1 - 0.3 * rainLevel) + brightAdd);
        double cp = Math.cos(pt), sp = Math.sin(pt), cyw = Math.cos(yw), syw = Math.sin(yw);
        final double fx = cp * syw, fy = sp, fz = cp * cyw;
        final double rx = cyw, rz = -syw;
        final double ux = -sp * syw, uy = cp, uz = -sp * cyw;
        final double tanH = fovTan;
        final double asp = (double) RW / RH;
        final boolean ht = !mc && hasTarget && targetMob == null;
        final int ttx = tx, tty = ty, ttz = tz;
        final boolean mineOn = !mc && mining;
        final int mnx = mineX, mny = mineY, mnz = mineZ;
        final double mprog = mineProg;

        final List<RPlayer> rl = mc ? new ArrayList<RPlayer>() : new ArrayList<RPlayer>(remotes.values());
        final List<Mob> vmobs = new ArrayList<>();
        if (!mc) for (Mob m0 : mobs) if (mobDist <= 0 || Math.hypot(m0.x - ex, m0.z - ez) < mobDist) vmobs.add(m0);
        final int nmob = vmobs.size();
        final int nrp = rl.size();
        final int nd = mc ? 0 : drops.size();
        final int nm = nmob + nrp + nd;
        final MobView[] views = new MobView[nm];
        for (int i = 0; i < nm; i++) {
            double px, py, pz, pang, pph, psw, prad, ptop;
            int ptype;
            boolean phurt;
            MobModels.Part[] pparts;
            if (i < nmob) {
                Mob m = vmobs.get(i);
                px = m.x; py = m.y; pz = m.z; pang = m.ang; pph = m.phase; psw = m.swing;
                ptype = m.type; phurt = m.hurt > 0; pparts = MobModels.MODEL[m.type];
                prad = MobModels.RAD[ptype]; ptop = MobModels.H[ptype] + 0.15;
            } else if (i < nmob + nrp) {
                RPlayer r = rl.get(i - nmob);
                px = r.x; py = r.y; pz = r.z; pang = r.yaw; pph = r.phase; psw = r.swing;
                ptype = MobModels.PLAYER; phurt = false; pparts = MobModels.PLAYER_MODELS[r.id & 3];
                prad = MobModels.RAD[ptype]; ptop = MobModels.H[ptype] + 0.15;
            } else {
                Drop d = drops.get(i - nmob - nrp);
                px = d.x; py = d.y + 0.1 + 0.06 * Math.sin(d.age * 3); pz = d.z; pang = d.age * 2; pph = 0; psw = 0;
                ptype = MobModels.PLAYER; phurt = false; pparts = itemParts(d.item);
                prad = 0.4; ptop = 0.55;
            }
            MobView mv = new MobView();
            mv.mx = px; mv.my = py; mv.mz = pz;
            mv.c = Math.cos(pang); mv.s = Math.sin(pang);
            mv.rad = prad;
            mv.top = ptop;
            mv.hurt = phurt;
            mv.parts = pparts;
            if (i >= nmob && i < nmob + nrp) {
                RPlayer rp = rl.get(i - nmob);
                mv.skin = skinFor(rp);
                mv.headIdx = 0;
                double ha = Math.max(-1.3, Math.min(1.3, rp.pitch));
                mv.hs = Math.sin(ha); mv.hc = Math.cos(ha);
            }
            mv.box = new double[mv.parts.length * 6];
            double sw = Math.sin(pph) * psw * MobModels.SWING[ptype];
            for (int k = 0; k < mv.parts.length; k++) {
                MobModels.Part p = mv.parts[k];
                double off = p.grp * sw;
                mv.box[k * 6] = p.x0; mv.box[k * 6 + 1] = p.y0; mv.box[k * 6 + 2] = p.z0 + off;
                mv.box[k * 6 + 3] = p.x1; mv.box[k * 6 + 4] = p.y1; mv.box[k * 6 + 5] = p.z1 + off;
            }
            mv.lit = lightAt(w, amb, (int) Math.floor(px), (int) Math.floor(py + 1), (int) Math.floor(pz), Math.hypot(px - ex, pz - ez));
            views[i] = mv;
        }

        // подписи над другими игроками: координаты на экране (0..1), если игрока видно
        List<Object[]> nt = new ArrayList<>();
        for (RPlayer r : rl) {
            double vx = r.x - ex, vy = r.y + 2.2 - ey, vz = r.z - ez;
            double zc = vx * fx + vy * fy + vz * fz;
            if (zc < 0.5) continue;
            double sXn = (vx * rx + vz * rz) / (zc * tanH * asp), sYn = (vx * ux + vy * uy + vz * uz) / (zc * tanH);
            if (Math.abs(sXn) > 1.15 || Math.abs(sYn) > 1.15) continue;
            double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double tw = w.cast(ex, ey, ez, vx / dist, vy / dist, vz / dist, dist, new int[7], new double[2]);
            if (tw >= 0 && tw < dist - 0.9) continue;           // закрыт блоками
            nt.add(new Object[]{r.name, (sXn + 1) / 2, (1 - sYn) / 2, dist, r.creator});
        }
        tagList = nt;

        final int RWf = RW, RHf = RH;
        Runnable job = () -> IntStream.range(0, RHf).parallel().forEach(y -> {
            int[] o = new int[7];
            int[] ax = new int[2];
            double[] wd = new double[2], gl = new double[3];
            double sY = (1 - 2 * (y + 0.5) / RHf) * tanH;
            for (int x = 0; x < RWf; x++) {
                double sX = (2 * (x + 0.5) / RWf - 1) * tanH * asp;
                double dx = fx + rx * sX + ux * sY;
                double dy = fy + uy * sY;
                double dz = fz + rz * sX + uz * sY;
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= l; dy /= l; dz /= l;
                gl[0] = -1;
                double t = w.cast(ex, ey, ez, dx, dy, dz, maxd, o, wd, gl);
                int col;

                // мобы (по частям, в локальной системе координат моба)
                int bi = -1, bp = 0, bax = 0, bsg = 0;
                double lim = t < 0 ? maxd : t, bt = 0;
                for (int i = 0; i < nm; i++) {
                    MobView mv = views[i];
                    double tb = rayBox(ex, ey, ez, dx, dy, dz, mv.mx - mv.rad, mv.my, mv.mz - mv.rad,
                            mv.mx + mv.rad, mv.my + mv.top, mv.mz + mv.rad, ax);
                    if (tb < 0 || tb >= lim) continue;
                    double rx0 = ex - mv.mx, rz0 = ez - mv.mz;
                    double lox = rx0 * mv.c - rz0 * mv.s, loz = rx0 * mv.s + rz0 * mv.c, loy = ey - mv.my;
                    double ldx = dx * mv.c - dz * mv.s, ldz = dx * mv.s + dz * mv.c;
                    for (int k = 0; k < mv.parts.length; k++) {
                        double[] bx6 = mv.box;
                        double oy_ = loy, oz_ = loz, dy_ = dy, dz_ = ldz;
                        if (k == mv.headIdx) {                                                // голова наклоняется по взгляду
                            double py0 = loy - mv.pivY;
                            oy_ = py0 * mv.hc + loz * mv.hs + mv.pivY; oz_ = -py0 * mv.hs + loz * mv.hc;
                            dy_ = dy * mv.hc + ldz * mv.hs; dz_ = -dy * mv.hs + ldz * mv.hc;
                        }
                        double tm = rayBox(lox, oy_, oz_, ldx, dy_, dz_, bx6[k * 6], bx6[k * 6 + 1], bx6[k * 6 + 2],
                                bx6[k * 6 + 3], bx6[k * 6 + 4], bx6[k * 6 + 5], ax);
                        if (tm >= 0 && tm < lim) { lim = tm; bi = i; bp = k; bt = tm; bax = ax[0]; bsg = ax[1]; }
                    }
                }

                if (bi >= 0) {
                    MobView mv = views[bi];
                    MobModels.Part pt2 = mv.parts[bp];
                    double rx0 = ex - mv.mx, rz0 = ez - mv.mz;
                    double lhx = rx0 * mv.c - rz0 * mv.s + (dx * mv.c - dz * mv.s) * bt;
                    double lhy = ey - mv.my + dy * bt;
                    double lhz = rx0 * mv.s + rz0 * mv.c + (dx * mv.s + dz * mv.c) * bt;
                    int base = pt2.col;
                    int hh = ih((int) Math.floor(lhx * 10) + bax * 7, (int) Math.floor(lhy * 10), (int) Math.floor(lhz * 10));
                    double var = 0.92 + ((hh & 15) / 15.0) * 0.12;
                    if (pt2.tex >= 100 && mv.skin != null) {
                        double hyy = lhy, hzz = lhz;
                        if (bp == mv.headIdx) {
                            double py0 = lhy - mv.pivY;
                            hyy = py0 * mv.hc + lhz * mv.hs + mv.pivY; hzz = -py0 * mv.hs + lhz * mv.hc;
                        }
                        double[] bb = mv.box;
                        double fx1 = (lhx - bb[bp * 6]) / (bb[bp * 6 + 3] - bb[bp * 6]), fy1 = (hyy - bb[bp * 6 + 1]) / (bb[bp * 6 + 4] - bb[bp * 6 + 1]),
                                fz1 = (hzz - bb[bp * 6 + 2]) / (bb[bp * 6 + 5] - bb[bp * 6 + 2]);
                        base = skinAt(mv.skin, pt2.tex - 100, clamp01(fx1), clamp01(fy1), clamp01(fz1), bax, bsg);
                        var = 1;
                    } else if (pt2.tex == 1) { if (noise(lhx * 3 + lhy * 2, lhz * 3 - lhy * 1.5, 77) > 0.52) base = 0xF0ECE4; }
                    else if (pt2.tex == 2) var = 0.8 + ((hh & 15) / 15.0) * 0.3;
                    double face = bax == 1 ? (bsg > 0 ? 1.0 : 0.5) : bax == 0 ? 0.8 : 0.65;
                    col = scale(base, face * var * mv.lit);
                    if (mv.hurt) col = mix(col, 0xFF2020, 0.5);
                    double f = bt / maxd;
                    col = mix(col, skyHorC, Math.min(1, f * f * fogK));
                } else if (t < 0) {
                    col = skyColor(dx, dy, dz);
                } else {
                    int bx = o[0], by = o[1], bz = o[2], nx = o[3], ny = o[4], nz = o[5], b = o[6];
                    double hx = ex + dx * t, hy = ey + dy * t, hz = ez + dz * t;
                    double u, v;
                    if (nx != 0) { u = frac(hy); v = frac(hz); }
                    else if (ny != 0) { u = frac(hx); v = frac(hz); }
                    else { u = frac(hx); v = frac(hy); }
                    int base = BASE[b];
                    if (b == 1) {
                        if (ny == 1) base = BASE[1];
                        else if (ny == -1) base = BASE[2];
                        else base = frac(hy) > 0.8 ? BASE[1] : BASE[2];
                    } else if (b == 6 && ny != 0) base = 0xA9854F;
                    double light = ny == 1 ? 1.0 : ny == -1 ? 0.55 : nx != 0 ? 0.8 : 0.65;
                    int face = (nx != 0 ? 1 : 0) + (ny != 0 ? 2 : 0) + (nz != 0 ? 3 : 0) + (nx + ny + nz > 0 ? 10 : 0);
                    int h = ih(bx * 31 + by * 17 + bz * 13 + face, (int) (u * 8), (int) (v * 8));
                    double var = (b == 5 ? 0.75 : 0.9) + ((h & 15) / 15.0) * (b == 5 ? 0.25 : 0.1);
                    if (b == B_COAL || b == B_IRON || b == B_GOLD || b == B_DIAM) {      // вкрапления руды
                        int hh2 = ih(bx * 5 + face, by * 7 + (int) (u * 5), bz * 3 + (int) (v * 5));
                        if ((hh2 & 7) < 3) base = b == B_COAL ? 0x1E1E1E : b == B_IRON ? 0xD8A878 : b == B_GOLD ? 0xF0C030 : 0x40E0E0;
                    } else if (b == 13 || b == 22) {                                      // швы кирпичной кладки
                        double rowv = b == 13 ? frac(hy * 4) : frac(hy * 2);
                        double off = b == 13 ? ((int) Math.floor(hy * 4) & 1) * 0.5 : ((int) Math.floor(hy * 2) & 1) * 0.5;
                        double colu = frac((nx != 0 ? hz : hx) * (b == 13 ? 2 : 1) + off);
                        if (rowv < 0.12 || colu < 0.07) base = scale(base, 0.7);
                    } else if (b == B_SHELF) {
                        double fy2 = frac(hy * 2);
                        if (ny != 0) base = 0x8A6A3A;
                        else if (fy2 < 0.12 || fy2 > 0.94) base = 0x6A4A22;
                        else { int bk = (int) Math.floor((nx != 0 ? hz : hx) * 6); int hv = ih(bk, (int) Math.floor(hy * 2), 7);
                            base = new int[]{0xA83030, 0x3050A0, 0x308040, 0xC8A030, 0x703080, 0xD8D0C0}[(hv & 7) % 6]; if (frac((nx != 0 ? hz : hx) * 6) < 0.1) base = scale(base, 0.6); }
                    } else if (b == B_OBSI) {
                        int ho = ih(bx * 3 + face, by * 7 + (int) (u * 6), bz * 5 + (int) (v * 6));
                        if ((ho & 15) == 0) base = 0x7A4AC8; else if ((ho & 7) == 1) base = 0x120A20;
                    } else if (b >= 64 && b <= 84) {
                        double fa = nx != 0 ? hz : hx, fb = ny != 0 ? hz : hy;
                        if (b == 72) { if (frac(fa * 2) < 0.04 || frac(fb * 2) < 0.04) base = scale(base, 0.86); }
                        else if (b == 73) { if (frac(fb * 4) < 0.03) base = scale(base, 0.93); }
                        else if (b == 74) {
                            double rv = frac(fb * 2), of = ((int) Math.floor(fb * 2) & 1) * 0.5, cu = frac(fa * 2 + of);
                            if (rv < 0.08 || cu < 0.06) base = scale(base, 0.72); else if ((h & 7) == 0) base = 0x6ABEA8;
                        } else if (b == 75) { if ((h & 7) < 2) base = 0x4A7A3A; else if ((h & 15) == 5) base = 0x3A5A2A; }
                        else if (b == 76) {
                            if (ny != 0) { double ee = Math.min(Math.min(frac(hx), 1 - frac(hx)), Math.min(frac(hz), 1 - frac(hz))); if (ee < 0.1) base = scale(base, 0.82); }
                            else if (frac(fa * 4) < 0.1) base = scale(base, 0.84);
                        } else if (b >= 77 && b <= 80) {
                            double ee = Math.min(Math.min(frac(fa), 1 - frac(fa)), Math.min(frac(fb), 1 - frac(fb)));
                            if (ee < 0.08) base = scale(base, 0.78); else if (ee < 0.16) base = scale(base, 1.12); else if ((h & 7) == 0) base = scale(base, 1.15);
                        } else if (b == 81) {
                            int row = (int) Math.floor(fb * 4);
                            if (frac(fb * 4) < 0.06 || frac(fa * 2 + (row & 1) * 0.5) < 0.05) base = scale(base, 0.72);
                        } else if (b == 82) {
                            if (frac(fb * 4) < 0.08) base = scale(base, 0.85); else if ((h & 15) == 0) base = 0x988868;
                        } else if (b == 83) {
                            base = (((int) Math.floor(fa * 2) + (int) Math.floor(fb * 2)) & 1) == 0 ? 0xF0F0F0 : 0x202024;
                        } else if (b == 84) {
                            double ee = Math.min(Math.min(frac(fa), 1 - frac(fa)), Math.min(frac(fb), 1 - frac(fb)));
                            base = ee < 0.12 ? 0x6A5A30 : ee < 0.2 ? 0xFFC860 : 0xFFF0C0;
                        }
                    } else if (b == B_TNT) {
                        double ty2 = frac(hy);
                        if (ny != 0) base = (Math.abs(frac(hx) - 0.5) < 0.12 || Math.abs(frac(hz) - 0.5) < 0.12) ? 0xE8E0D0 : 0xC83030;
                        else if (ty2 > 0.33 && ty2 < 0.67) { base = 0xEEE6D8; double tu = frac(nx != 0 ? hz : hx); if (ty2 > 0.4 && ty2 < 0.6 && (Math.abs(tu - 0.2) < 0.06 || Math.abs(tu - 0.5) < 0.06 || Math.abs(tu - 0.8) < 0.06)) base = 0x2A2A2A; }
                        else base = frac((nx != 0 ? hz : hx) * 8) < 0.06 ? 0xA02020 : 0xC83030;
                        if (!fuses.isEmpty() && fuses.containsKey(tkey(bx, by, bz)) && (((int) (animT * 7)) & 1) == 0) base = mix(base, 0xFFFFFF, 0.8);
                    } else if (b == B_NRACK) {
                        int hn = ih(bx * 5 + face, by * 3 + (int) (u * 5), bz * 11 + (int) (v * 5));
                        if ((hn & 7) == 0) base = 0x5A1E1E; else if ((hn & 15) == 1) base = 0x9A4A3A;
                    } else if (b == B_MAGMA) {
                        int hm = ih(bx * 3 + face + (int) (animT * 2), by * 7 + (int) (u * 4), bz * 5 + (int) (v * 4));
                        base = (hm & 3) == 0 ? 0xFFC040 : (hm & 3) == 1 ? 0xB02800 : 0xE05A10;
                    } else if (b == B_LANT) {
                        double lu = frac(nx != 0 ? hz : hx), lv2 = frac(hy);
                        if (lu < 0.12 || lu > 0.88 || lv2 < 0.1 || lv2 > 0.9) base = 0x3A3A3A; else base = 0xFFD870;
                    } else if (b == 4 || b == 21) {                                       // полосы досок
                        if (frac(hy * 4) < 0.08) base = scale(base, 0.8);
                    } else if (b == B_DOOR_L || b == B_DOOR_U || b == B_TRAP) {
                        double hh1 = nx != 0 ? hz : hx, fy1 = frac(hy), fh1 = frac(hh1);
                        if (b == B_TRAP) {
                            double pu = ny != 0 ? frac(hx) : fy1, pv = ny != 0 ? frac(hz) : fh1;
                            if (pu < 0.08 || pu > 0.92 || pv < 0.08 || pv > 0.92 || Math.abs(pu - 0.5) < 0.04) base = scale(base, 0.68);
                            else if (ny != 0 && Math.abs(pu - 0.5) < 0.14 && pv > 0.78 && pv < 0.9) base = 0xC8C8C8;
                        } else {
                            if (fh1 < 0.07 || fh1 > 0.93) base = scale(base, 0.68);
                            else if (fy1 > 0.12 && fy1 < 0.88 && fh1 > 0.2 && fh1 < 0.8 && (b == B_DOOR_U ? fy1 < 0.8 : true)) base = scale(base, 0.82);
                            if (b == B_DOOR_L && fy1 > 0.85 && fy1 < 0.97 && fh1 > 0.82 && fh1 < 0.92) base = 0xE0C040;
                        }
                    } else if (b >= B_CHEST && b <= B_SANDST) {
                        double hh0 = nx != 0 ? hz : hx, fy0 = frac(hy), fh = frac(hh0);
                        if (b == B_CHEST) {                                               // крышка, замок, окантовка
                            if (fy0 < 0.07 || fy0 > 0.93 || fh < 0.06 || fh > 0.94) base = scale(base, 0.65);
                            else if (ny == 0 && fy0 > 0.6 && fy0 < 0.68) base = scale(base, 0.7);
                            else if (ny == 0 && Math.abs(fh - 0.5) < 0.07 && fy0 > 0.48 && fy0 < 0.74) base = 0xE0C040;
                        } else if (b == B_FURN) {                                         // каменная печь с топкой
                            if (ny == 0 && Math.abs(fh - 0.5) < 0.3 && fy0 > 0.1 && fy0 < 0.55) base = (fy0 < 0.22 && Math.abs(fh - 0.5) < 0.24) ? 0xC85A10 : 0x202020;
                            else if (ny == 1 && Math.abs(fh - 0.5) < 0.25 && Math.abs(frac(hz) - 0.5) < 0.25) base = scale(base, 0.6);
                            else if ((h & 7) == 0) base = scale(base, 0.85);
                        } else if (b == B_BED) {
                            if (ny == 1) base = (frac(hz) > 0.72 || frac(hz) < 0.05) ? 0xF2F2F2 : 0xC83C3C;
                            else base = fy0 < 0.22 ? 0x8A5A2A : 0xC83C3C;
                        } else if (b == B_CROP || b == B_RIPE) {
                            int stalk = (int) Math.floor(fh * 6) + (int) Math.floor((nx != 0 ? hx : hz) * 6);
                            boolean ripe = b == B_RIPE;
                            base = (stalk & 1) == 0 ? (ripe ? 0xE2C050 : 0x58B040) : (ripe ? 0xB8962C : 0x3A8A2C);
                            if (ripe && fy0 < 0.3) base = 0x4A9A30;
                        } else if (b == B_CACTUS) {
                            if (frac(hh0 * 4) < 0.13 || frac((nx != 0 ? hz : hx) * 4) < 0.13) base = scale(base, 0.72);
                            if ((h & 15) == 0) base = 0xE8E8C0;                               // иголки
                        } else {                                                          // песчаник: слои
                            if (frac(hy * 3) < 0.12) base = scale(base, 0.82);
                        }
                    }
                    double lit, warm = 0;
                    if (b == B_GLOW || b == B_LANT || b == B_MAGMA || b == B_LAMP) {
                        lit = 1.3;
                        int gs = ih(bx * 3 + face, by * 5 + (int) (u * 6), bz * 7 + (int) (v * 6));   // яркие и тёмные вкрапления
                        base = mix(base, (gs & 3) == 0 ? 0xFFF6C8 : (gs & 7) == 1 ? 0xE8A030 : base, 0.7);
                    } else {
                        int lx = bx + nx, ly = by + ny, lz = bz + nz;
                        double sk = skyLit(w, amb, lx, ly, lz, t), bk = blockLit(w, lx, ly, lz);
                        lit = Math.max(sk, bk);
                        if (bk > sk) warm = Math.min(1, (bk - sk) * 1.8);
                    }
                    light *= var * lit;
                    if (ht && bx == ttx && by == tty && bz == ttz
                            && (u < 0.04 || u > 0.96 || v < 0.04 || v > 0.96)) light *= 0.45;
                    if (mineOn && bx == mnx && by == mny && bz == mnz) {
                        int hh = ih(bx * 7 + by * 3 + bz + face, (int) (u * 16), (int) (v * 16));
                        if (((hh >>> 8) & 255) / 255.0 < mprog * 0.9) light *= 0.4;
                    }
                    col = scale(base, light);
                    if (warm > 0) col = warmTint(col, warm);
                    double f = t / maxd;
                    f = f * f * fogK;
                    if (f > 0.001) col = mix(col, skyHorC, Math.min(1, f));
                }
                if (wd[0] > 0 && bi < 0) {                       // луч шёл сквозь воду
                    double k = Math.min(0.93, Math.max(0.30, 1 - Math.exp(-wd[0] * 0.20)));
                    double rip = 1;
                    if (wd[1] >= 0) {
                        int hh3 = ih((int) Math.floor((ex + dx * wd[1]) * 2.2), (int) Math.floor((ez + dz * wd[1]) * 2.2), (int) (animT * 1.2));
                        rip = 0.93 + ((hh3 & 15) / 15.0) * 0.14;
                    }
                    col = mix(col, scale(BASE[WATER], (0.25 + 0.75 * daylight) * rip), k);
                }
                if (gl[0] >= 0 && (t < 0 || gl[0] < t) && (bi < 0 || bt > gl[0])) {          // сквозь стекло
                    double px = ex + dx * gl[0], py = ey + dy * gl[0], pz = ez + dz * gl[0];
                    double fu = gl[1] == 0 ? frac(py) : frac(px), fv = gl[1] == 2 ? frac(py) : frac(pz);
                    if (gl[1] == 1) { fu = frac(px); fv = frac(pz); }
                    double em = Math.min(Math.min(fu, 1 - fu), Math.min(fv, 1 - fv));
                    double dl = 0.35 + 0.65 * daylight;
                    if (gl[2] == B_PORTAL) {
                        double ang = Math.atan2(fv - 0.5, fu - 0.5), rr = Math.hypot(fu - 0.5, fv - 0.5);
                        double sw = Math.sin(ang * 3 + rr * 16 - animT * 3.2) * 0.5 + 0.5, sw2 = Math.sin(fu * 9 + fv * 7 + animT * 2.1) * 0.5 + 0.5;
                        int pc = mix(0x2A0670, 0xB058FF, sw * 0.7 + sw2 * 0.3);
                        int spk = ih((int) (fu * 8), (int) (fv * 8), (int) (animT * 5));
                        if ((spk & 15) == 0) pc = 0xF2D2FF;
                        col = mix(col, pc, 0.72);
                    } else if (gl[2] == B_GLASS) col = em < 0.06 ? mix(col, scale(0xE4F6FF, dl), 0.7) : mix(col, scale(0xBFE6F5, dl), 0.13);
                    else if (em < 0.09) col = mix(col, scale(0x8A5A2A, 0.5 + 0.5 * dl), 0.9);
                }
                pix[y * RWf + x] = col;
            }
        });
        runRender(job);
    }

    // ====================================================================
    //  Ввод
    // ====================================================================
    void setupInput() {
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_F11) { toggleFullscreen(); return; }
                if (k == KeyEvent.VK_F2) { synchronized (lock) { takeScreenshot(); } return; }
                synchronized (lock) {
                    if (state == State.SPLASH) { splashT = SPLASH_LEN + 1; return; }
                    if (state == State.PLAYING && chatOpen) {                       // набор сообщения в чат
                        if (k == KeyEvent.VK_ENTER) sendChat();
                        else if (k == KeyEvent.VK_ESCAPE) { chatOpen = false; chatText = ""; }
                        else if (k == KeyEvent.VK_BACK_SPACE && !chatText.isEmpty()) chatText = chatText.substring(0, chatText.length() - 1);
                        else if (k == KeyEvent.VK_V && e.isControlDown()) chatText = (chatText + clipboardText()).substring(0, Math.min(120, chatText.length() + clipboardText().length()));
                        return;
                    }
                    if (state == State.MENU && menuPage == 2) {                     // экран подключения
                        if (k == KeyEvent.VK_ESCAPE) { cancelJoin(); menuPage = 1; }
                        else if (k == KeyEvent.VK_ENTER) { if (!joining) connectTo(lastAddr); }
                        else if (k == KeyEvent.VK_TAB || k == KeyEvent.VK_UP || k == KeyEvent.VK_DOWN) field = 1 - field;
                        else if (k == KeyEvent.VK_BACK_SPACE) {
                            if (field == 0 && !lastAddr.isEmpty()) lastAddr = lastAddr.substring(0, lastAddr.length() - 1);
                            else if (field == 1 && !myName.isEmpty()) myName = myName.substring(0, myName.length() - 1);
                        } else if (k == KeyEvent.VK_V && e.isControlDown()) {
                            String c = clipboardText().trim();
                            if (field == 0) lastAddr = c.substring(0, Math.min(60, c.length()));
                            else myName = filterNick(c);
                        }
                        return;
                    }
                    if (state == State.MENU && menuPage == 20) {
                        if (k == KeyEvent.VK_ESCAPE) menuPage = 0;
                        else if (k == KeyEvent.VK_ENTER) saveAccount();
                        else if (k == KeyEvent.VK_TAB || k == KeyEvent.VK_DOWN) field = (field + 1) % 3;
                        else if (k == KeyEvent.VK_UP) field = (field + 2) % 3;
                        else if (k == KeyEvent.VK_BACK_SPACE) {
                            if (field == 0 && !myName.isEmpty()) myName = myName.substring(0, myName.length() - 1);
                            else if (field == 1 && !acctPass.isEmpty()) acctPass = acctPass.substring(0, acctPass.length() - 1);
                            else if (field == 2 && !skinPath.isEmpty()) skinPath = skinPath.substring(0, skinPath.length() - 1);
                        } else if (k == KeyEvent.VK_V && e.isControlDown()) {
                            String c = clipboardText().trim();
                            if (field == 0) myName = filterNick(c);
                            else if (field == 1) acctPass = c.substring(0, Math.min(32, c.length()));
                            else skinPath = c.substring(0, Math.min(250, c.length()));
                        }
                        return;
                    }
                    if (state == State.MENU && menuPage >= 5 && k == KeyEvent.VK_ESCAPE) { menuPage = 4; return; }
                    if (state == State.MENU && (menuPage == 1 || menuPage == 3 || menuPage == 4) && k == KeyEvent.VK_ESCAPE) { menuPage = 0; delConfirm = ""; menuNote = ""; return; }
                    if (sleeping && (k == KeyEvent.VK_W || k == KeyEvent.VK_A || k == KeyEvent.VK_S || k == KeyEvent.VK_D || k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ESCAPE)) { wakeUp(); return; }
                    if (k < 256) keys[k] = true;
                    if (k == KeyEvent.VK_F3) showInfo = !showInfo;
                    if (k == KeyEvent.VK_J && state == State.PLAYING && !chatOpen) achOpen = !achOpen;
                    if (k == KeyEvent.VK_ESCAPE && achOpen) { achOpen = false; return; }
                    if (k == KeyEvent.VK_M && state == State.PLAYING && !chatOpen) { Opt mo = optMap.get("minimap"); mo.set(mo.get() + 1); applyOpts(); saveConfig(); mapT = 0; }
                    if (state == State.PLAYING) {
                        if (k == KeyEvent.VK_ESCAPE) { if (panel != 0) closePanel(); else pause(); }
                        else if (panel != 0 && k == KeyEvent.VK_UP) panelSel = (panelSel + curList().length - 1) % curList().length;
                        else if (panel != 0 && k == KeyEvent.VK_DOWN) panelSel = (panelSel + 1) % curList().length;
                        else if (panel != 0 && k == KeyEvent.VK_ENTER) doCraft(panelSel);
                        else if (k == KeyEvent.VK_F && !survival) { fly = !fly; pl.vy = 0; }
                        else if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_9) sel = k - KeyEvent.VK_1;
                        else if ((k == KeyEvent.VK_E || (k == KeyEvent.VK_C && survival)) && panel != 2) togglePanel();
                        else if (k == KeyEvent.VK_V) eat();
                        else if (k == KeyEvent.VK_Q) dropFromUi(e.isControlDown() || e.isShiftDown());
                        else if (k == KeyEvent.VK_T && isNet()) { chatOpen = true; chatText = ""; chatOpenT = animT; Arrays.fill(keys, false); lmb = false; }
                    } else if (state == State.PAUSED && k == KeyEvent.VK_ESCAPE) {
                        if (menuPage >= 5) menuPage = 4; else if (menuPage == 4) menuPage = 0; else resume();
                    } else if (state == State.PAUSED && k == KeyEvent.VK_P && server != null) {
                        pvp = !pvp; sendFlags(); announce("PvP " + (pvp ? "включён" : "выключен") + " хостом");
                    }
                }
            }

            public void keyTyped(KeyEvent e) {
                char ch = e.getKeyChar();
                if (ch < 32 || ch == 127) return;
                synchronized (lock) {
                    if (state == State.PLAYING && chatOpen) {
                        if (chatText.length() < 120 && !(ch == 't' && chatText.isEmpty() && animT - chatOpenT < 0.15)) chatText += ch;
                    } else if (state == State.MENU && menuPage == 20) {
                        if (field == 0 && myName.length() < 16 && nickChar(ch)) myName += ch;
                        else if (field == 1 && acctPass.length() < 32) acctPass += ch;
                        else if (field == 2 && skinPath.length() < 250) skinPath += ch;
                    } else if (state == State.MENU && menuPage == 2) {
                        if (field == 0 && lastAddr.length() < 60) lastAddr += ch;
                        else if (field == 1 && myName.length() < 16 && nickChar(ch)) myName += ch;
                    }
                }
            }

            public void keyReleased(KeyEvent e) {
                int k = e.getKeyCode();
                if (k < 256) keys[k] = false;
            }
        });

        addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                synchronized (lock) {
                    if (state == State.PLAYING) pause();
                }
            }
        });

        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                synchronized (lock) { handleClick(e); }
            }

            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) lmb = false;
            }

            public void mouseMoved(MouseEvent e) { mpx = e.getX(); mpy = e.getY(); look(e); }

            public void mouseDragged(MouseEvent e) { mpx = e.getX(); mpy = e.getY(); look(e); }

            public void mouseWheelMoved(MouseWheelEvent e) {
                if (state == State.PLAYING) sel = ((sel + (wheelInv ? -1 : 1) * e.getWheelRotation()) % 9 + 9) % 9;
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    static String clipboardText() {
        try {
            Object o = Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            return o == null ? "" : o.toString().replace('\n', ' ').replace('\r', ' ');
        } catch (Exception ex) {
            return "";
        }
    }

    void handleClick(MouseEvent e) {
        switch (state) {
            case SPLASH:
                splashT = SPLASH_LEN + 1;
                break;
            case MENU:
            case PAUSED:
            case DEAD:
                if (e.getButton() == MouseEvent.BUTTON3 && (state == State.MENU || state == State.PAUSED)) {
                    for (Btn b : buttons()) if (b.r != null && b.contains(e.getX(), e.getY())) { snd("click"); b.r.run(); return; }
                }
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (state == State.MENU && menuPage == 20) {
                        int fy = fieldY0(), bw = Math.min(480, getWidth() - 40), bx = (getWidth() - bw) / 2;
                        if (e.getX() >= bx && e.getX() < bx + bw)
                            for (int i = 0; i < 3; i++) if (e.getY() >= fy + i * 70 + 20 && e.getY() < fy + i * 70 + 56) field = i;
                    }
                    if (state == State.MENU && menuPage == 2) {
                        int fy = fieldY0();
                        int bx = (getWidth() - Math.min(480, getWidth() - 40)) / 2, bw = Math.min(480, getWidth() - 40);
                        if (e.getX() >= bx && e.getX() < bx + bw) {
                            if (e.getY() >= fy + 20 && e.getY() < fy + 56) field = 0;
                            else if (e.getY() >= fy + 90 && e.getY() < fy + 126) field = 1;
                        }
                    }
                    for (Btn b : buttons()) {
                        if (b.contains(e.getX(), e.getY())) { snd("click"); b.a.run(); return; }
                    }
                }
                break;
            case PLAYING:
                if (panel == 1) { invClick(e.getX(), e.getY(), e.getButton(), e.isShiftDown()); return; }
                if (panel == 3) { tileClick(e.getX(), e.getY(), e.getButton()); return; }
                if (!captured) { capture(); return; }
                if (e.getButton() == MouseEvent.BUTTON1) { lmb = true; mineT = 0; }
                else if (e.getButton() == MouseEvent.BUTTON3) {
                    if (targetMob == null && !e.isShiftDown() && useTargetBlock()) { }
                    else if (foodValue(hot[sel]) > 0 && survival) eatItem(hot[sel]);
                    else if (targetMob != null && targetMob.type == MobModels.VILLAGER) {
                        if (survival) { panel = 2; panelSel = 0; }
                        else { msg = "Житель: торговля работает в режиме выживания"; msgT = 2; }
                    } else placeBlock();
                }
                break;
        }
    }

    void look(MouseEvent e) {
        if (!captured || state != State.PLAYING) return;
        int dx = e.getXOnScreen() - cx, dy = e.getYOnScreen() - cy;
        if (dx == 0 && dy == 0) return;
        yaw += dx * 0.0025 * sensK;
        pitch -= dy * 0.0025 * sensK * (invY ? -1 : 1);
        pitch = Math.max(-1.55, Math.min(1.55, pitch));
        robot.mouseMove(cx, cy);
    }

    void capture() {
        try {
            if (robot == null) robot = new Robot();
            Point p = getLocationOnScreen();
            cx = p.x + getWidth() / 2;
            cy = p.y + getHeight() / 2;
            robot.mouseMove(cx, cy);
            setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
                    new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank"));
            captured = true;
        } catch (Exception ex) {
            captured = false;
        }
    }

    void release() {
        captured = false;
        setCursor(Cursor.getDefaultCursor());
    }

    // ====================================================================
    //  Кнопки меню
    // ====================================================================
    void addBtns(List<Btn> l, int w, int y0, String[] t, Runnable[] a) {
        int avail = getHeight() - y0 - 36;
        int bh = Math.max(30, Math.min(46, avail / Math.max(1, t.length) - 9)), gap = Math.max(6, bh / 4);
        int bw = Math.min(480, w - 40);
        for (int i = 0; i < t.length; i++) {
            Btn b = new Btn();
            b.t = t[i];
            b.a = a[i];
            b.w = bw;
            b.h = bh;
            b.x = (w - bw) / 2;
            b.y = y0 + i * (bh + gap);
            l.add(b);
        }
    }

    int fieldY0() { return Math.max(150, (int) (getHeight() * 0.26)); }

    List<Btn> buttons() {
        List<Btn> l = new ArrayList<>();
        int w = getWidth(), h = getHeight();
        switch (state) {
            case MENU: {
                if (menuPage == 1) {
                    List<String> t1 = new ArrayList<>();
                    List<Runnable> a1 = new ArrayList<>();
                    t1.add("Открыть мир для друзей: Выживание (новый)"); a1.add(() -> { startGame(true); startHosting(); });
                    t1.add("Открыть мир для друзей: Творчество (новый)"); a1.add(() -> { startGame(false); startHosting(); });
                    if (hasSave) {
                        t1.add("Открыть для друзей: " + disp(slot) + " (сохранённый)"); a1.add(() -> { loadGame(); startHosting(); });
                        if (worlds.size() > 1) { t1.add("Выбрать другой мир ▶"); a1.add(() -> { int k = worlds.indexOf(slot); slot = worlds.get((k + 1) % worlds.size()); saveConfig(); refreshSaveInfo(); }); }
                    }
                    t1.add("Подключиться к другу по IP"); a1.add(() -> { menuPage = 2; field = 0; joinStatus = ""; menuNote = ""; });
                    t1.add("Назад"); a1.add(() -> menuPage = 0);
                    addBtns(l, w, Math.max(150, (int) (h * 0.30)), t1.toArray(new String[0]), a1.toArray(new Runnable[0]));
                    break;
                }
                if (menuPage == 2) {
                    int by = fieldY0() + 150;
                    addBtns(l, w, by, new String[]{joining ? "Отмена" : "Подключиться", "Назад"},
                            new Runnable[]{() -> { if (joining) cancelJoin(); else connectTo(lastAddr); }, () -> { cancelJoin(); menuPage = 1; }});
                    break;
                }
                if (menuPage == 3) {
                    List<String> t3 = new ArrayList<>();
                    List<Runnable> a3 = new ArrayList<>();
                    List<Runnable> r3 = new ArrayList<>();
                    int from = worldPage * 5;
                    if (from >= worlds.size()) { worldPage = 0; from = 0; }
                    for (int i = from; i < Math.min(worlds.size(), from + 5); i++) {
                        final String wn = worlds.get(i);
                        t3.add(disp(wn) + " — " + worldInfos.get(i));
                        a3.add(() -> { slot = wn; saveConfig(); loadGame(); });
                        r3.add(() -> {
                            if (delConfirm.equals(wn)) { deleteWorld(wn); delConfirm = ""; menuNote = "Мир удалён"; }
                            else { delConfirm = wn; menuNote = "Нажмите ПКМ по миру «" + disp(wn) + "» ещё раз, чтобы удалить его навсегда"; }
                        });
                    }
                    if (worlds.size() > 5) { t3.add("Другие миры ▶ (" + (worldPage + 1) + "/" + ((worlds.size() + 4) / 5) + ")"); a3.add(() -> worldPage = (worldPage + 1) % ((worlds.size() + 4) / 5)); r3.add(null); }
                    t3.add("Новый мир: Выживание"); a3.add(() -> startGame(true)); r3.add(null);
                    t3.add("Новый мир: Творчество"); a3.add(() -> startGame(false)); r3.add(null);
                    t3.add("Сид нового мира: " + menuSeed + "  (сменить)"); a3.add(this::newSeed); r3.add(null);
                    t3.add("Назад"); a3.add(() -> { menuPage = 0; delConfirm = ""; menuNote = ""; }); r3.add(null);
                    int y3 = Math.max(150, (int) (h * 0.30));
                    int n0 = l.size();
                    addBtns(l, w, y3, t3.toArray(new String[0]), a3.toArray(new Runnable[0]));
                    for (int i = 0; i < r3.size(); i++) l.get(n0 + i).r = r3.get(i);
                    break;
                }
                if (menuPage == 20) {
                    addBtns(l, w, fieldY0() + 3 * 70 + 24, new String[]{"Сохранить аккаунт", "Убрать скин", "Назад"},
                            new Runnable[]{this::saveAccount, () -> { skinPath = ""; acctMsg = loadMySkin(); saveConfig(); }, () -> menuPage = 0});
                    break;
                }
                if (menuPage >= 4) { settingsButtons(l, w, Math.max(150, (int) (h * 0.30))); break; }
                addBtns(l, w, Math.max(150, (int) (h * 0.32)), new String[]{"Одиночная игра", "Сетевая игра", "Аккаунт", "Настройки", "Выход"},
                        new Runnable[]{() -> { menuPage = 3; worldPage = 0; delConfirm = ""; menuNote = ""; refreshSaveInfo(); },
                                () -> { menuPage = 1; menuNote = ""; refreshSaveInfo(); },
                                () -> { menuPage = 20; field = 0; acctMsg = ""; acctPass = ""; },
                                () -> { menuPage = 4; menuNote = ""; },
                                this::shutdownNow});
                break;
            }
            case PAUSED: {
                List<String> tp = new ArrayList<>();
                List<Runnable> ap = new ArrayList<>();
                if (menuPage >= 4) { settingsButtons(l, w, 130); break; }
                tp.add("Продолжить"); ap.add(this::resume);
                tp.add("Настройки"); ap.add(() -> menuPage = 4);
                if (!isNet()) { tp.add("Открыть мир для друзей по сети"); ap.add(this::startHosting); }
                tp.add(isClient() ? "Отключиться и выйти в меню" : "Сохранить и выйти в меню"); ap.add(this::toMenu);
                addBtns(l, w, h / 2 - 20, tp.toArray(new String[0]), ap.toArray(new Runnable[0]));
                break;
            }
            case DEAD:
                addBtns(l, w, h / 2 + 10, new String[]{"Возродиться", "В главное меню"},
                        new Runnable[]{this::respawn, this::toMenu});
                break;
            default:
                break;
        }
        return l;
    }

    // ====================================================================
    //  Отрисовка интерфейса
    // ====================================================================
    void shadowText(Graphics2D g, String s, int x, int y, Color c) {
        g.setColor(Color.BLACK);
        g.drawString(s, x + 2, y + 2);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    void centerText(Graphics2D g, String s, int cxx, int y, Color c) {
        int sw = g.getFontMetrics().stringWidth(s);
        shadowText(g, s, cxx - sw / 2, y, c);
    }

    void drawTitle(Graphics2D g, String s, int cxx, int baseY, int size, float alpha) {
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, size);
        g.setFont(f);
        int x = cxx - g.getFontMetrics().stringWidth(s) / 2;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        int depth = Math.max(4, size / 9);
        for (int i = depth; i >= 1; i--) {
            g.setColor(new Color(mix(0x5A5A5A, 0x1C1C1C, i / (double) depth)));
            g.drawString(s, x + i, baseY + i);
        }
        g.setPaint(new GradientPaint(0, baseY - size, new Color(0xF4F4F4), 0, baseY, new Color(0x9C9C9C)));
        g.drawString(s, x, baseY);
        g.setComposite(old);
    }

    void drawButtons(Graphics2D g) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        for (Btn b : buttons()) {
            boolean hov = b.contains(mpx, mpy);
            g.setColor(new Color(hov ? 0x8E92B8 : 0x6B6B6B));
            g.fillRect(b.x, b.y, b.w, b.h);
            g.setColor(new Color(0xD0D0D0));
            g.fillRect(b.x, b.y, b.w, 3);
            g.fillRect(b.x, b.y, 3, b.h);
            g.setColor(new Color(0x2A2A2A));
            g.fillRect(b.x, b.y + b.h - 3, b.w, 3);
            g.fillRect(b.x + b.w - 3, b.y, 3, b.h);
            g.setColor(Color.BLACK);
            g.drawRect(b.x - 1, b.y - 1, b.w + 1, b.h + 1);
            centerText(g, b.t, b.x + b.w / 2, b.y + b.h / 2 + 7, hov ? new Color(0xFFFFA0) : Color.WHITE);
        }
    }

    void drawCube(Graphics2D g, int cxx, int cyy, int s, int id, boolean bright) {
        int half = s / 2, q = s / 4;
        int topC = id == 6 ? 0xA9854F : BASE[id];
        int sideC = id == 1 ? BASE[2] : BASE[id];
        double k = bright ? 1 : 0.3;
        g.setColor(new Color(scale(topC, k)));
        g.fillPolygon(new int[]{cxx, cxx + half, cxx, cxx - half}, new int[]{cyy - half, cyy - q, cyy, cyy - q}, 4);
        g.setColor(new Color(scale(sideC, 0.8 * k)));
        g.fillPolygon(new int[]{cxx - half, cxx, cxx, cxx - half}, new int[]{cyy - q, cyy, cyy + half, cyy + q}, 4);
        g.setColor(new Color(scale(sideC, 0.6 * k)));
        g.fillPolygon(new int[]{cxx, cxx + half, cxx + half, cxx}, new int[]{cyy, cyy - q, cyy + q, cyy + half}, 4);
    }

    static final String[] HEART = {".XX...XX.", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...", "....X...."};

    void drawFood(Graphics2D g, int x, int y, int mode) {      // окорок: 2 полный, 1 половина, 0 пустой
        Color full = new Color(0xC8742C), dark = new Color(0x3A2A1A);
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) {
            double dx = c - 5.0, dy = r - 3.5;
            boolean meat = dx * dx + dy * dy < 14 && r < 7;
            boolean bone = (r >= 5 && c <= 3 && c + (8 - r) >= 3 && c + (8 - r) <= 6) || (r == 7 && c <= 2);
            if (!meat && !bone) continue;
            boolean on = mode == 2 || (mode == 1 && c >= 4);
            Color cc = bone && !meat ? (on ? new Color(0xEFEFE0) : new Color(0x44403A)) : on ? (dx < -1 ? new Color(0xE0924A) : full) : dark;
            g.setColor(cc);
            g.fillRect(x + c * 2, y + r * 2, 2, 2);
        }
    }

    // ---------- мини-карта ----------
    BufferedImage mapImg;
    double mapT;
    int mapCX = Integer.MIN_VALUE, mapCZ;

    void updateMinimap(double dt) {
        mapT -= dt;
        if (mapSize == 0 || mapT > 0 || world == null || pl.x >= NT) return;
        mapT = 0.4;
        int S = mapSize, px0 = (int) Math.floor(pl.x), pz0 = (int) Math.floor(pl.z);
        if (mapImg == null || mapImg.getWidth() != S) mapImg = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
        int half = S / 2;
        for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) {
            int wx = px0 - half + i, wz = pz0 - half + j;
            int top = world.hmapAt(wx, wz);
            int col;
            if (top < -100) col = 0x10141C;
            else {
                int b = world.get(wx, top, wz);
                boolean water = top <= SEA && world.get(wx, SEA, wz) == WATER;
                if (water) col = 0x2F5FC8;
                else {
                    col = b > 0 && b < BASE.length ? BASE[b] : 0x808080;
                    int tn = world.hmapAt(wx - 1, wz - 1);
                    if (tn > -100) col = scale(col, Math.max(0.6, Math.min(1.35, 1 + (top - tn) * 0.12)));
                    if (b == 5) col = scale(col, 0.85);
                }
            }
            mapImg.setRGB(i, j, col);
        }
    }

    void paintMinimap(Graphics2D g, int w, int h) {
        if (pl.x >= NT) return;
        if (mapSize == 0 || mapImg == null || state != State.PLAYING) return;
        int S = mapSize, sc = S >= 140 ? 1 : 1, x = w - S - 12 - 2, y = 84;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(x - 3, y - 3, S + 6, S + 6);
        g.drawImage(mapImg, x, y, S, S, null);
        g.setColor(new Color(255, 255, 255, 140));
        g.drawRect(x - 3, y - 3, S + 5, S + 5);
        int half = S / 2;
        int bx = (int) Math.floor(pl.x) - half, bz = (int) Math.floor(pl.z) - half;
        for (RPlayer r : remotes.values()) {
            int rx = (int) Math.floor(r.x) - bx, rz = (int) Math.floor(r.z) - bz;
            if (rx < 0 || rz < 0 || rx >= S || rz >= S) continue;
            g.setColor(r.creator ? new Color(0xFFE040) : Color.WHITE);
            g.fillRect(x + rx - 2, y + rz - 2, 4, 4);
        }
        for (Mob m : mobs) {
            if (!m.hostile()) continue;
            int rx = (int) Math.floor(m.x) - bx, rz = (int) Math.floor(m.z) - bz;
            if (rx < 0 || rz < 0 || rx >= S || rz >= S) continue;
            g.setColor(new Color(0xFF3030));
            g.fillRect(x + rx - 1, y + rz - 1, 3, 3);
        }
        // стрелка игрока (север сверху; взгляд yaw=0 направлен в +z, то есть вниз по карте)
        double ang = yaw;
        int cx2 = x + half, cz2 = y + half;
        int[] ax = {cx2 + (int) (Math.sin(ang) * 7), cx2 + (int) (Math.sin(ang + 2.5) * 5), cx2 + (int) (Math.sin(ang - 2.5) * 5)};
        int[] az = {cz2 + (int) (Math.cos(ang) * 7), cz2 + (int) (Math.cos(ang + 2.5) * 5), cz2 + (int) (Math.cos(ang - 2.5) * 5)};
        g.setColor(Color.BLACK);
        g.fillPolygon(new int[]{ax[0], ax[1] + 0, ax[2]}, new int[]{az[0], az[1], az[2]}, 3);
        g.setColor(new Color(0xFF4040));
        g.fillPolygon(ax, az, 3);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        shadowText(g, "С", x + S / 2 - 4, y + 12, Color.WHITE);
    }

    void takeScreenshot() {
        try {
            int w = getWidth(), h = getHeight();
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D gg = bi.createGraphics();
            paint(gg);
            gg.dispose();
            File dir = new File(dataDir(), "screenshots");
            dir.mkdirs();
            String nm = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date()) + ".png";
            javax.imageio.ImageIO.write(bi, "png", new File(dir, nm));
            msg = "Скриншот сохранён: " + nm;
            msgT = 3;
        } catch (Exception e) {
            msg = "Не удалось сохранить скриншот";
            msgT = 3;
        }
    }

    void drawHeart(Graphics2D g, int x, int y, int mode) {   // mode: 2 полное, 1 половина, 0 пустое
        for (int r = 0; r < HEART.length; r++) {
            for (int c = 0; c < 9; c++) {
                if (HEART[r].charAt(c) != 'X') continue;
                boolean on = mode == 2 || (mode == 1 && c <= 4);
                g.setColor(on ? ((r == 1 && c < 3) ? new Color(0xFF8A8A) : new Color(0xE02020)) : new Color(0x3C1C1C));
                g.fillRect(x + c * 2, y + r * 2, 2, 2);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        int w = getWidth(), h = getHeight();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smoothImg ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, 0, 0, w, h, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        switch (state) {
            case SPLASH: paintSplash(g, w, h); break;
            case MENU: paintMenu(g, w, h); break;
            default: paintHud(g, w, h); break;
        }
    }

    void paintSplash(Graphics2D g, int w, int h) {
        double t = splashT;
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(0, 0, w, h);
        float a = (float) clamp01((t - 0.8) / 1.0);
        drawTitle(g, "MiniCraft", w / 2, (int) (h * 0.42 - (1 - a) * 40), Math.min(w / 7, 120), a);
        float a2 = (float) clamp01((t - 2.0) / 0.8);
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a2));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        centerText(g, "Сделано на чистой Java · без сторонних библиотек", w / 2, (int) (h * 0.42 + 56), new Color(0xE8E8E8));
        if (t > 2.8 && ((int) (t * 2) % 2 == 0)) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            centerText(g, "Нажмите любую клавишу", w / 2, h - 60, new Color(0xD0D0D0));
        }
        g.setComposite(old);
        g.setColor(new Color(0, 0, 0, (int) (255 * clamp01(1 - t / 1.2))));
        g.fillRect(0, 0, w, h);
    }

    void paintMenu(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(0, 0, w, h);
        drawTitle(g, "MiniCraft", w / 2, Math.max(110, (int) (h * 0.2)), Math.min(w / 8, 96), 1f);
        if (menuPage == 2) paintJoin(g, w, h);
        drawButtons(g);
        if (menuPage == 20) paintAccount(g, w, h);
        else if (menuPage >= 4) paintSettingsTitle(g, w, Math.max(150, (int) (h * 0.30)));
        if (menuPage == 3) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            centerText(g, "Одиночная игра — выберите мир", w / 2, Math.max(150, (int) (h * 0.30)) - 28, Color.WHITE);
            if (menuPage == 3 && worlds.size() > 0) {
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                centerText(g, "ЛКМ — играть, ПКМ дважды — удалить мир", w / 2, Math.max(150, (int) (h * 0.30)) - 10, new Color(0xDDDDDD));
            }
        }
        if (menuPage == 1) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            centerText(g, "Один игрок открывает свой мир, друзья подключаются к нему по IP (порт " + Net.PORT + ")", w / 2, Math.max(150, (int) (h * 0.30)) - 14, new Color(0xE8E8E8));
        }
        if (!menuNote.isEmpty() && menuPage != 2) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            centerText(g, menuNote, w / 2, h - 40, new Color(0xFFB0A0));
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        shadowText(g, "MiniCraft 1.7.0 · Java, только стандартный JDK", 10, h - 12, new Color(0xDDDDDD));
    }

    void paintJoin(Graphics2D g, int w, int h) {
        int fy = fieldY0(), bw = Math.min(480, w - 40), bx = (w - bw) / 2;
        String[] lab = {"Адрес хоста (IP или IP:порт). Ctrl+V — вставить", "Ваше имя в игре"};
        String[] val = {lastAddr, myName};
        for (int i = 0; i < 2; i++) {
            int y = fy + i * 70;
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            shadowText(g, lab[i], bx, y + 14, new Color(0xE8E8E8));
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(bx, y + 20, bw, 36);
            g.setColor(field == i ? new Color(0xFFE890) : new Color(0x888888));
            g.drawRect(bx, y + 20, bw, 36);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
            String t = val[i] + (field == i && ((int) (animT * 2) % 2 == 0) ? "_" : "");
            g.setColor(Color.WHITE);
            g.drawString(t, bx + 10, y + 44);
        }
        if (!joinStatus.isEmpty()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            centerText(g, joinStatus, w / 2, fy + 142, joining ? new Color(0xFFF0A0) : new Color(0xFFB0A0));
        }
    }

    void paintNames(Graphics2D g, int w, int h) {
        List<Object[]> tl = tagList;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        for (Object[] t : tl) {
            String name = (String) t[0];
            int sx = (int) ((Double) t[1] * w), sy = (int) ((Double) t[2] * h);
            int tw = g.getFontMetrics().stringWidth(name);
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRect(sx - tw / 2 - 5, sy - 15, tw + 10, 20);
            shadowText(g, name, sx - tw / 2, sy, t.length > 4 && (Boolean) t[4] ? new Color(0xFFE040) : Color.WHITE);
        }
    }

    void paintChat(Graphics2D g, int w, int h) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        int y = h - 112;
        for (int i = chatLog.size() - 1; i >= 0 && y > 80; i--) {
            double age = animT - chatTime.get(i);
            if (!chatOpen && age > 8) break;
            float a = chatOpen ? 1f : (float) clamp01((8 - age) / 1.5);
            g.setColor(new Color(0, 0, 0, (int) (110 * a)));
            String cl = chatLog.get(i);
            int tw = g.getFontMetrics().stringWidth(cl.replace("\u0001", "").replace("\u0002", ""));
            g.fillRect(8, y - 15, tw + 12, 20);
            int ce = cl.startsWith("\u0001") ? cl.indexOf('\u0002') : -1;
            if (ce > 0) {
                String cn = cl.substring(1, ce);
                g.setColor(new Color(255, 224, 64, (int) (255 * a)));
                g.drawString(cn, 14, y);
                g.setColor(new Color(255, 255, 255, (int) (255 * a)));
                g.drawString(cl.substring(ce + 1), 14 + g.getFontMetrics().stringWidth(cn), y);
            } else {
                g.setColor(new Color(255, 255, 255, (int) (255 * a)));
                g.drawString(cl, 14, y);
            }
            y -= 22;
        }
        if (chatOpen) {
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(8, h - 100, Math.min(w - 16, 620), 26);
            g.setColor(new Color(0xFFE890));
            g.drawRect(8, h - 100, Math.min(w - 16, 620), 26);
            g.setColor(Color.WHITE);
            g.drawString("> " + chatText + (((int) (animT * 2) % 2 == 0) ? "_" : ""), 16, h - 82);
        }
    }

    int armColor() {
        int[] sk = mySkin != null ? mySkin : defaultSkin(myName);
        if (sk != null && sk.length >= 4096) { int c = sk[30 * 64 + 46]; if ((c >>> 24) > 200) return c & 0xFFFFFF; }
        return 0xC8A078;
    }

    /** Рука справа внизу с предметом: качается при ходьбе, взмахивает при ударе и установке блока. */
    void drawHand(Graphics2D g, int w, int h) {
        int ac = armColor();
        double sp = Math.min(1, Math.hypot(pl.vx - kbx, pl.vz - kbz) / 5) * (pl.onGround ? 1 : 0.2);
        double s = swingT > 0 ? 1 - swingT : 0, sw = Math.sin(s * Math.PI);
        double bobx = Math.sin(animT * 7) * sp * h * 0.008, boby = Math.abs(Math.cos(animT * 7)) * sp * h * 0.012;
        Graphics2D g2 = (Graphics2D) g.create();
        double ox = w * 0.82 + bobx - sw * h * 0.05, oy = h * 1.03 + boby + equipT * h * 0.30 - sw * h * 0.05;
        g2.translate(ox, oy);
        g2.rotate(-0.30 - sw * 0.5);
        int aw = (int) (h * 0.15), al = (int) (h * 0.42);
        int id = hot[sel];
        g2.setColor(new Color(scale(ac, 0.62)));
        g2.fillRoundRect(-aw / 2 + 4, -al + 4, aw, al, 8, 8);
        g2.setColor(new Color(scale(ac, 0.9)));
        g2.fillRoundRect(-aw / 2, -al, aw, al, 8, 8);
        g2.setColor(new Color(scale(ac, 1.12)));
        g2.fillRect(-aw / 2 + 3, -al + 3, aw / 4, al - 14);
        if (id > 0 && (!survival || inv[id] > 0)) {
            int is = (int) (h * (id >= 32 && id < 48 ? 0.17 : 0.2));
            drawIcon(g2, -aw / 2 + 6, -al - is / 4, is, id, true);
        }
        g2.dispose();
    }

    void paintHud(Graphics2D g, int w, int h) {
        paintWeather(g, w, h);
        if (state == State.PLAYING && panel == 0 && !sleeping && hp > 0) drawHand(g, w, h);
        if (critT > 0) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22 + (int) ((1 - critT) * 10)));
            centerText(g, "КРИТ!", w / 2 + 70, h / 2 - 20 - (int) ((1 - critT) * 30), new Color(255, 220, 60, (int) (255 * Math.min(1, critT * 1.5))));
        }
        if (showStatus && state == State.PLAYING && world != null) {
            String[] bn = {"Равнины", "Пустыня", "Снежный лес"}, wn = {"ясно", "дождь", "гроза"};
            int hh = (int) (((tod * 24) + 6) % 24), mm = (int) ((((tod * 24) + 6) % 1) * 60);
            String info = bn[biomeHere()] + " · " + wn[Math.max(0, Math.min(2, weather))] + String.format(" · %02d:%02d · ", hh, mm) + (int) pl.x + " " + (int) pl.y + " " + (int) pl.z;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(new Color(0, 0, 0, 150));
            g.drawString(info, 11, 57);
            g.setColor(new Color(0xF0F0F0));
            g.drawString(info, 10, 56);
        }
        if (showFps) { g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13)); g.setColor(Color.WHITE); g.drawString("FPS " + fps, w - 70, 40); }
        if (pvp) { g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13)); g.setColor(new Color(0xFF5050)); g.drawString("PvP", w - 44, 22); }
        if (hurtT > 0) {
            g.setColor(new Color(200, 0, 0, (int) Math.min(120, hurtT * 300)));
            g.fillRect(0, 0, w, h);
        }
        // прицел
        if (panel != 1 && panel != 3 && crossStyle != 2) {
            Color cc = targetMob != null || targetPl != null ? new Color(0xFF6060) : new Color(CROSS_COLS[crossCol]);
            if (crossStyle == 1) {
                g.setColor(Color.BLACK);
                g.fillOval(w / 2 - 4, h / 2 - 4, 8, 8);
                g.setColor(cc);
                g.fillOval(w / 2 - 3, h / 2 - 3, 6, 6);
            } else {
                g.setStroke(new BasicStroke(4));
                g.setColor(Color.BLACK);
                g.drawLine(w / 2 - 9, h / 2, w / 2 + 9, h / 2);
                g.drawLine(w / 2, h / 2 - 9, w / 2, h / 2 + 9);
                g.setStroke(new BasicStroke(2));
                g.setColor(cc);
                g.drawLine(w / 2 - 8, h / 2, w / 2 + 8, h / 2);
                g.drawLine(w / 2, h / 2 - 8, w / 2, h / 2 + 8);
            }
        }

        // панель быстрого доступа
        int size = 48, gap = 8, total = 9 * size + 8 * gap;
        int x0 = (w - total) / 2, y0 = h - size - 18;
        for (int j = 0; j < 9; j++) {
            int bx = x0 + j * (size + gap);
            int id = hot[j];
            drawSlot(g, bx, y0, size, id, id > 0 && survival ? inv[id] : -1, j == sel, false);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g.setColor(new Color(255, 255, 255, 200));
            g.drawString(String.valueOf(j + 1), bx + 3, y0 + 12);
        }
        if (hot[sel] > 0) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            centerText(g, NAMES[hot[sel]], w / 2, y0 - (survival ? 38 : 12), Color.WHITE);
        }

        // сытость
        if (survival && hungerOn) {
            int fi = (int) Math.ceil(food);
            for (int i = 0; i < 10; i++) {
                int mode = fi >= i * 2 + 2 ? 2 : fi == i * 2 + 1 ? 1 : 0;
                drawFood(g, x0 + total - 18 - (9 - i) * 20 + 18 * 0 + 0, y0 - 28, mode);
            }
        }
        if (survival && (inv[I_ARM_I] > 0 || inv[I_ARM_D] > 0)) drawIcon(g, x0 + 12, y0 - 50, 20, inv[I_ARM_D] > 0 ? I_ARM_D : I_ARM_I, true);
        if (achOpen) {
            int pw = 380, ph = 60 + ACH.length * 24, px = (w - pw) / 2, py = (h - ph) / 2;
            g.setColor(new Color(0, 0, 0, 190)); g.fillRoundRect(px, py, pw, ph, 14, 14);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            centerText(g, "Достижения " + ach.size() + "/" + ACH.length + "  (J - закрыть)", w / 2, py + 30, Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            for (int i = 0; i < ACH.length; i++) {
                boolean on = ach.contains(ACH[i][0]);
                g.setColor(on ? new Color(0xFFD860) : new Color(0x888888));
                g.drawString((on ? "[x] " : "[ ] ") + ACH[i][1] + " - " + ACH[i][2], px + 20, py + 58 + i * 24);
            }
        }
        // сердца
        if (survival) {
            for (int i = 0; i < 10; i++) {
                int mode = hp >= i * 2 + 2 ? 2 : hp == i * 2 + 1 ? 1 : 0;
                drawHeart(g, x0 + i * 20, y0 - 28, mode);
            }
        }
        if (showNotes && msgT > 0 && state == State.PLAYING) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            centerText(g, msg, w / 2, h / 2 + 50, new Color(0xFFF0A0));
        }

        // подсказки
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        String help = survival
                ? "WASD — ходьба | Пробел — прыжок/плыть | 1-9 / колесо — ячейка | ЛКМ — добывать / бить | ПКМ — ставить / говорить / есть"
                : "WASD — ходьба | Пробел — прыжок/плыть | F — полёт (Пробел/Shift) | 1-9 / колесо — ячейка | ЛКМ — ломать | ПКМ — ставить";
        if (showHints) shadowText(g, help, 10, 20, Color.WHITE);
        if (showHints) shadowText(g, survival ? "E — инвентарь и крафт | Q — выбросить | V — съесть | F3 — инфо | M — карта | F2 — скриншот | Esc — пауза" : "E — все блоки | Q — выбросить | F3 — инфо | Esc — пауза", 10, 38, Color.WHITE);

        if (survival) {
            // пузырьки воздуха
            if (air < 10) {
                for (int i = 0; i < Math.ceil(air); i++) {
                    g.setColor(new Color(0x9ADCFF));
                    g.fillOval(x0 + total - 14 - i * 16, y0 - 50, 12, 12);
                    g.setColor(new Color(0x3C86C8));
                    g.drawOval(x0 + total - 14 - i * 16, y0 - 50, 12, 12);
                }
            }
            if (showTools) drawInventory(g, w);
        }
        paintMinimap(g, w, h);
        if (sleeping) {
            sleepFade = Math.min(1.6, sleepFade + 0.02);
            g.setColor(new Color(0, 0, 10, (int) (Math.min(0.85, sleepFade * 0.5) * 255)));
            g.fillRect(0, 0, w, h);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            centerText(g, "Zzz...  Спят: " + Math.max(1, sleepN) + " из " + Math.max(1, sleepTotal), w / 2, h / 2 - 10, new Color(0xCFD8FF));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            centerText(g, "W/A/S/D, Пробел или Esc — встать", w / 2, h / 2 + 20, new Color(0xB0B8D8));
        } else sleepFade = 0;
        if (panel == 1) drawInventoryScreen(g, w, h);
        else if (panel == 3) drawTileScreen(g, w, h);
        else if (panel == 2) drawPanel(g, w, h);
        if (isNet()) {
            paintNames(g, w, h);
            paintChat(g, w, h);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            int pc = 1 + (server != null ? (int) server.conns.stream().filter(c -> c.ready).count() : remotes.size() + 0);
            String st = server != null ? "Вы хост · игроков: " + pc + " · порт " + Net.PORT : "Сетевая игра · игроков: " + (remotes.size() + 1);
            if (server == null && remotes.isEmpty()) st = "Сетевая игра · игроков: 2+";
            shadowText(g, st + " · T — чат", w - g.getFontMetrics().stringWidth(st + " · T — чат") - 10, h - 6, new Color(0xC8FFC8));
        }

        if (showInfo) {
            int cnt = world.count;
            String[] info = {
                    "FPS: " + fps,
                    String.format("XYZ: %.1f / %.1f / %.1f", pl.x, pl.y, pl.z),
                    "Чанков в памяти: " + cnt + " (радиус " + world.R + ", экономия " + (world.saver ? "вкл" : "выкл") + ")",
                    String.format("Время суток: %.2f (%s), зомби: %d", tod, daylight > 0.4 ? "день" : "ночь", mobs.size()),
                    "Сид: " + world.seed
            };
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
            for (int i = 0; i < info.length; i++) shadowText(g, info[i], 10, 44 + i * 16, new Color(0xE0FFE0));
        }

        if (state == State.PAUSED) {
            g.setColor(new Color(0, 0, 0, 130));
            g.fillRect(0, 0, w, h);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
            if (menuPage >= 4) paintSettingsTitle(g, w, 130); else centerText(g, "Пауза", w / 2, h / 2 - 60, Color.WHITE);
            drawButtons(g);
            if (server != null && menuPage == 0) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                int y = h / 2 + 160;
                centerText(g, "Мир открыт. Друзья подключаются по IP:", w / 2, y, new Color(0xA8FFA8));
                List<String> ips = Net.localAddresses();
                if (ips.isEmpty()) ips.add("127.0.0.1");
                for (String ip : ips) { y += 22; centerText(g, ip + ":" + Net.PORT, w / 2, y, Color.WHITE); }
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                centerText(g, "Через интернет: пробросьте порт " + Net.PORT + " (TCP)", w / 2, y + 22, new Color(0xDDDDDD));
                centerText(g, "на роутере и дайте другу свой внешний IP", w / 2, y + 40, new Color(0xDDDDDD));
                centerText(g, "P — PvP: " + (pvp ? "включён" : "выключен"), w / 2, y + 64, pvp ? new Color(0xFF7070) : new Color(0xDDDDDD));
            }
        } else if (state == State.DEAD) {
            g.setColor(new Color(150, 0, 0, 120));
            g.fillRect(0, 0, w, h);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 54));
            centerText(g, "Вы погибли!", w / 2, h / 2 - 50, Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            if (survival) centerText(g, "Место гибели: " + (int) pl.x + ", " + (int) pl.y + ", " + (int) pl.z + " — там лежат ваши вещи", w / 2, h / 2 - 20, new Color(0xFFDDDD));
            drawButtons(g);
        }
    }

    void drawInventory(Graphics2D g, int w) {                 // инструменты в углу экрана
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        int x = w - 190, y = 20;
        String[] tn = {"Кирка", "Топор", "Меч"};
        int[] tv = {pick, axe, sword};
        for (int i = 0; i < 3; i++) {
            shadowText(g, tn[i] + ": " + TIER_NAMES[tv[i]], x, y, tv[i] > 0 ? new Color(0xFFE890) : new Color(0xBBBBBB));
            y += 18;
        }
    }

    void drawPanel(Graphics2D g, int w, int h) {
        Recipe[] list = curList();
        int pw = 470, rows = Math.min(10, list.length), rh = 40;
        int px0 = 14, py0 = 70, ph = rows * rh + 56;
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRect(px0, py0, pw, ph);
        g.setColor(new Color(255, 255, 255, 90));
        g.drawRect(px0, py0, pw, ph);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        shadowText(g, panel == 2 ? "Торговля с жителем" : "Крафт", px0 + 12, py0 + 24, new Color(0xFFE890));
        int first = Math.max(0, Math.min(panelSel - rows / 2, list.length - rows));
        for (int j = 0; j < rows; j++) {
            int i = first + j;
            Recipe r = list[i];
            boolean ok = canCraft(r);
            int ry = py0 + 36 + j * rh;
            if (i == panelSel) {
                g.setColor(new Color(255, 255, 255, 50));
                g.fillRect(px0 + 4, ry, pw - 8, rh - 4);
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            shadowText(g, r.name, px0 + 12, ry + 16, ok ? new Color(0xA8FFA8) : new Color(0xAAAAAA));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            shadowText(g, "нужно: " + reqText(r), px0 + 12, ry + 31, ok ? new Color(0xE0E0E0) : new Color(0x888888));
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        shadowText(g, "↑↓ — выбор   Enter — " + (panel == 2 ? "обменять" : "создать") + "   Esc — закрыть", px0 + 12, py0 + ph - 8, Color.WHITE);
    }

    // ====================================================================
    //  Главный цикл
    // ====================================================================
    @Override
    public void run() {
        long last = System.nanoTime(), fpsT = last;
        int frames = 0;
        while (true) {
            long now = System.nanoTime();
            double dt = Math.min(0.05, (now - last) / 1e9);
            last = now;
            try {
                synchronized (lock) {
                    update(dt);
                    if (autoPause && state == State.PLAYING && !isFocusOwner() && !chatOpen) pause();
                    if (++frameNo % renderEvery == 0 || state == State.SPLASH) render();
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            repaint();
            frames++;
            if (now - fpsT >= 1_000_000_000L) { fps = frames; frames = 0; fpsT = now; }
            long spentMs = (System.nanoTime() - now) / 1_000_000L;
            try { Thread.sleep(Math.max(1, fpsMs - spentMs)); } catch (InterruptedException e) { return; }   // ограничение ~60 FPS
        }
    }

    static JFrame frame;
    static boolean fullscreen;

    /** F11: полный экран без рамки и обратно. */
    void toggleFullscreen() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) return;
            fullscreen = !fullscreen;
            GraphicsDevice dev = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            frame.dispose();
            frame.setUndecorated(fullscreen);
            if (fullscreen) {
                if (dev.isFullScreenSupported()) dev.setFullScreenWindow(frame);
                else { frame.setExtendedState(JFrame.MAXIMIZED_BOTH); frame.setVisible(true); }
            } else {
                dev.setFullScreenWindow(null);
                frame.setExtendedState(JFrame.NORMAL);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
            requestFocusInWindow();
        });
    }

    public static void main(String[] args) {
        if (args.length == 2 && args[0].equals("--verifier")) {            // выдаёт значение CREATOR_VERIFY для нового пароля создателя
            System.out.println(verifierOf(proofOf(CREATOR_NICK, args[1])));
            return;
        }
        SwingUtilities.invokeLater(() -> {
            MiniCraft game = new MiniCraft();
            JFrame f = new JFrame("MiniCraft");
            frame = f;
            f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            f.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) { game.shutdown(); }
            });
            f.setContentPane(game);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            game.requestFocusInWindow();
            Thread t = new Thread(game, "game-loop");
            t.setDaemon(true);
            t.start();
        });
    }
}

/** Деревни: детерминированно по сиду и координатам, чтобы соседние чанки стыковались. */
final class Villages {
    static final int CELL = 160;          // размер ячейки сетки, в которой может быть одна деревня
    static final int WATER = 10;

    static final class House {
        int x, z, w, d, kind, side;       // side: 0 дверь на +x, 1 на -x, 2 на +z, 3 на -z

        int doorX() { return side == 0 ? x + w - 1 : side == 1 ? x : x + w / 2; }

        int doorZ() { return side == 2 ? z + d - 1 : side == 3 ? z : z + d / 2; }

        /** Клетка перед дверью, на n блоков наружу. */
        int outX(int n) { return doorX() + (side == 0 ? n : side == 1 ? -n : 0); }

        int outZ(int n) { return doorZ() + (side == 2 ? n : side == 3 ? -n : 0); }
    }

    static final class Village {
        int id, cx, cz, y0;
        final List<House> houses = new ArrayList<>();
    }

    static final Village NONE = new Village();

    static Village cell(MiniCraft.World w, int ix, int iz) {
        long key = MiniCraft.World.key(ix, iz);
        Village v = w.vcache.get(key);
        if (v == null) {
            v = make(w, ix, iz);
            w.vcache.put(key, v);
        }
        return v;
    }

    static Village make(MiniCraft.World w, int ix, int iz) {
        int sd = w.seed;
        if (MiniCraft.hash2(ix, iz, sd + 5001) > 0.55) return NONE;
        int vx = ix * CELL + 40 + (int) (MiniCraft.hash2(ix, iz, sd + 5002) * (CELL - 80));
        int vz = iz * CELL + 40 + (int) (MiniCraft.hash2(ix, iz, sd + 5003) * (CELL - 80));
        int y0 = w.heightAt(vx, vz);
        if (y0 <= MiniCraft.SEA + 3 || y0 >= MiniCraft.SNOW_H - 6) return NONE;
        Village v = new Village();
        v.id = ix * 7919 + iz * 104729 + 1;
        v.cx = vx;
        v.cz = vz;
        v.y0 = y0;
        int n = 8 + (int) (MiniCraft.hash2(ix, iz, sd + 5004) * 4);
        for (int k = 0; k < n; k++) {
            double ang = k * 2 * Math.PI / n + (MiniCraft.hash2(ix + k, iz, sd + 5005) - 0.5) * 0.4;
            double rad = (k % 2 == 0 ? 14 : 24) + MiniCraft.hash2(ix, iz + k, sd + 5006) * 5;
            int hw = 5 + (int) (MiniCraft.hash2(ix + k, iz + 1, sd + 5007) * 3);
            int hd = 5 + (int) (MiniCraft.hash2(ix + 2, iz + k, sd + 5008) * 3);
            int hx = vx + (int) Math.round(Math.cos(ang) * rad) - hw / 2;
            int hz = vz + (int) Math.round(Math.sin(ang) * rad) - hd / 2;
            boolean ok = true;
            for (int cxx = 0; cxx < 2 && ok; cxx++) {
                for (int czz = 0; czz < 2; czz++) {
                    int hh = w.heightAt(hx + cxx * (hw - 1), hz + czz * (hd - 1));
                    if (Math.abs(hh - y0) > 5 || hh <= MiniCraft.SEA + 1) { ok = false; break; }
                }
            }
            for (House o : v.houses) {
                if (hx < o.x + o.w + 3 && o.x < hx + hw + 3 && hz < o.z + o.d + 3 && o.z < hz + hd + 3) ok = false;
            }
            if (!ok) continue;
            House h = new House();
            h.x = hx; h.z = hz; h.w = hw; h.d = hd;
            h.kind = v.houses.size() % 4 == 0 ? 1 : 0;
            double dx = vx - (hx + hw / 2.0), dz = vz - (hz + hd / 2.0);
            h.side = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 0 : 1) : (dz > 0 ? 2 : 3);
            v.houses.add(h);
        }
        return v.houses.size() >= 3 ? v : NONE;
    }

    /** Все деревни, центры которых ближе r блоков к точке. */
    static List<Village> around(MiniCraft.World w, double x, double z, double r) {
        List<Village> out = new ArrayList<>();
        int i0 = Math.floorDiv((int) (x - r), CELL), i1 = Math.floorDiv((int) (x + r), CELL);
        int j0 = Math.floorDiv((int) (z - r), CELL), j1 = Math.floorDiv((int) (z + r), CELL);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                Village v = cell(w, i, j);
                if (v != NONE && Math.hypot(v.cx - x, v.cz - z) <= r) out.add(v);
            }
        }
        return out;
    }

    static boolean near(MiniCraft.World w, double x, double z, double r) {
        return !around(w, x, z, r).isEmpty();
    }

    // ---------- строительство внутри чанка ----------
    static int get(byte[] b, int ox, int oz, int wx, int y, int wz) {
        int lx = wx - ox, lz = wz - oz;
        if (lx < 0 || lz < 0 || lx >= 16 || lz >= 16 || y < 0 || y >= 64) return -1;
        return b[(y * 16 + lz) * 16 + lx];
    }

    static void set(byte[] b, int ox, int oz, int wx, int y, int wz, int id) {
        int lx = wx - ox, lz = wz - oz;
        if (lx < 0 || lz < 0 || lx >= 16 || lz >= 16 || y < 0 || y >= 64) return;
        b[(y * 16 + lz) * 16 + lx] = (byte) id;
    }

    /** Выравнивает столбец: подсыпает землю до y0, ставит верхний блок и расчищает воздух выше. */
    static void level(byte[] b, int ox, int oz, int wx, int wz, int y0, int topId) {
        if (wx < ox || wz < oz || wx >= ox + 16 || wz >= oz + 16) return;
        for (int y = 1; y < y0; y++) {
            int c = get(b, ox, oz, wx, y, wz);
            if (c == 0 || c == WATER) set(b, ox, oz, wx, y, wz, 2);
        }
        set(b, ox, oz, wx, y0, wz, topId);
        for (int y = y0 + 1; y <= y0 + 9; y++) set(b, ox, oz, wx, y, wz, 0);
    }

    static void apply(MiniCraft.World w, byte[] b, int cx, int cz) {
        int ox = cx * 16, oz = cz * 16;
        int i0 = Math.floorDiv(ox - 45, CELL), i1 = Math.floorDiv(ox + 16 + 45, CELL);
        int j0 = Math.floorDiv(oz - 45, CELL), j1 = Math.floorDiv(oz + 16 + 45, CELL);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                Village v = cell(w, i, j);
                if (v == NONE) continue;
                if (v.cx + 45 < ox || v.cx - 45 > ox + 16 || v.cz + 45 < oz || v.cz - 45 > oz + 16) continue;
                build(w, b, ox, oz, v);
            }
        }
    }

    static boolean inHouse(Village v, int x, int z, int margin) {
        for (House h : v.houses) {
            if (x >= h.x - margin && x < h.x + h.w + margin && z >= h.z - margin && z < h.z + h.d + margin) return true;
        }
        return false;
    }

    static void build(MiniCraft.World w, byte[] b, int ox, int oz, Village v) {
        int y0 = v.y0;
        // 1. дорожки от центра к каждому дому
        for (int x = ox; x < ox + 16; x++) {
            for (int z = oz; z < oz + 16; z++) {
                if (Math.abs(x - v.cx) > 36 || Math.abs(z - v.cz) > 36) continue;
                if (inHouse(v, x, z, 1)) continue;
                boolean path = Math.abs(x - v.cx) <= 2 && Math.abs(z - v.cz) <= 2;
                for (House h : v.houses) {
                    if (path) break;
                    double ax = v.cx, az = v.cz, bx = h.outX(1), bz = h.outZ(1);
                    double dx = bx - ax, dz = bz - az, t = ((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz);
                    t = Math.max(0, Math.min(1, t));
                    double px = ax + dx * t - x, pz = az + dz * t - z;
                    if (px * px + pz * pz < 1.5) path = true;
                }
                if (path && Math.abs(w.heightAt(x, z) - y0) <= 5 && w.heightAt(x, z) > MiniCraft.SEA) level(b, ox, oz, x, z, y0, 14);
            }
        }
        // 2. колодец
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = v.cx + dx, z = v.cz + dz;
                level(b, ox, oz, x, z, y0, 15);
                if (dx == 0 && dz == 0) {
                    for (int y = y0 - 3; y <= y0; y++) set(b, ox, oz, x, y, z, WATER);
                } else {
                    set(b, ox, oz, x, y0 + 1, z, 15);
                    if (dx != 0 && dz != 0) for (int y = y0 + 2; y <= y0 + 3; y++) set(b, ox, oz, x, y, z, 6);
                }
                set(b, ox, oz, x, y0 + 4, z, 4);
            }
        }
        // 3. дома
        for (House h : v.houses) house(b, ox, oz, h, y0);
    }

    static void house(byte[] b, int ox, int oz, House h, int y0) {
        if (h.x + h.w + 2 < ox || h.x - 2 >= ox + 16 || h.z + h.d + 2 < oz || h.z - 2 >= oz + 16) {
            lampOnly(b, ox, oz, h, y0);
            return;
        }
        int wall = h.kind == 1 ? 15 : 4, corner = h.kind == 1 ? 22 : 6, roof = h.kind == 1 ? 22 : 21;
        for (int x = h.x - 1; x <= h.x + h.w; x++) {
            for (int z = h.z - 1; z <= h.z + h.d; z++) {
                boolean inside = x >= h.x && x < h.x + h.w && z >= h.z && z < h.z + h.d;
                level(b, ox, oz, x, z, y0, inside ? (h.kind == 1 ? 22 : 4) : 1);
                if (!inside || x < ox || z < oz || x >= ox + 16 || z >= oz + 16) continue;
                int dx = x - h.x, dz = z - h.z;
                boolean edge = dx == 0 || dz == 0 || dx == h.w - 1 || dz == h.d - 1;
                boolean cor = (dx == 0 || dx == h.w - 1) && (dz == 0 || dz == h.d - 1);
                if (edge) {
                    boolean door = x == h.doorX() && z == h.doorZ();
                    for (int y = y0 + 1; y <= y0 + 4; y++) {
                        int id = cor ? corner : wall;
                        if (door && y <= y0 + 2) id = 0;
                        else if (!cor && !door && y == y0 + 2 && ((dx + dz) & 1) == 0) id = 0;       // окно
                        set(b, ox, oz, x, y, z, id);
                    }
                }
                int ins = Math.min(Math.min(dx, h.w - 1 - dx), Math.min(dz, h.d - 1 - dz));
                set(b, ox, oz, x, y0 + 5 + ins, z, roof);
            }
        }
        // кровать (шерсть) и настенная лампа напротив двери
        set(b, ox, oz, h.x + 1, y0 + 1, h.z + 1, 18);
        set(b, ox, oz, h.x + 2, y0 + 1, h.z + 1, 18);
        int lx = h.side == 0 ? h.x : h.side == 1 ? h.x + h.w - 1 : h.x + h.w / 2;
        int lz = h.side == 2 ? h.z : h.side == 3 ? h.z + h.d - 1 : h.z + h.d / 2;
        set(b, ox, oz, lx, y0 + 3, lz, 19);
        lampOnly(b, ox, oz, h, y0);
    }

    /** Столб с фонарём перед дверью. */
    static void lampOnly(byte[] b, int ox, int oz, House h, int y0) {
        int px = h.outX(2) + (h.side >= 2 ? 1 : 0), pz = h.outZ(2) + (h.side < 2 ? 1 : 0);
        if (px < ox || pz < oz || px >= ox + 16 || pz >= oz + 16) return;
        level(b, ox, oz, px, pz, y0, 14);
        for (int y = y0 + 1; y <= y0 + 3; y++) set(b, ox, oz, px, y, pz, 6);
        set(b, ox, oz, px, y0 + 4, pz, 19);
    }
}

/** Модели мобов из прямоугольных частей. Начало координат - центр ступней, ось z смотрит вперёд. */
final class MobModels {
    static final int ZOMBIE = 0, PIG = 1, SHEEP = 2, COW = 3, CHICKEN = 4, VILLAGER = 5, PLAYER = 6;
    static final int TYPES = 7;
    static final double[] HW = {0.3, 0.35, 0.35, 0.4, 0.2, 0.3, 0.3};
    static final double[] H = {1.8, 0.85, 1.0, 1.15, 0.65, 1.8, 1.8};
    static final double[] HP = {14, 8, 8, 10, 4, 20, 20};
    static final double[] RAD = {0.95, 0.85, 0.85, 0.95, 0.55, 0.85, 0.95};   // радиус охватывающей коробки
    static final double[] SWING = {0.18, 0.14, 0.14, 0.14, 0.10, 0.18, 0.2};  // размах ног при ходьбе

    /** Вид текстуры: 0 обычная, 1 пятна коровы, 2 пушистая шерсть. */
    static final class Part {
        final double x0, y0, z0, x1, y1, z1;
        final int col, grp, tex;

        Part(double x0, double y0, double z0, double x1, double y1, double z1, int col, int grp, int tex) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0; this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.col = col; this.grp = grp; this.tex = tex;
        }
    }

    static final Part[][] MODEL = new Part[TYPES][];
    static final int[] SHIRTS = {0x2E9E4B, 0xC8442E, 0x3C6FD0, 0xD8B22C};
    static final Part[][] PLAYER_MODELS = new Part[4][];

    /** Другой игрок: цвет рубашки зависит от номера, чтобы друзей можно было различать. */
    static Part[] playerModel(int shirt) {
        final double q = 0.05625;                                    // 1 пиксель скина = 1/32 роста
        return new Part[]{
                new Part(-4 * q, 24 * q, -4 * q, 4 * q, 32 * q, 4 * q, 0xC8967A, 0, 100),      // голова
                new Part(-4 * q, 12 * q, -2 * q, 4 * q, 24 * q, 2 * q, shirt, 0, 101),         // туловище
                new Part(-8 * q, 12 * q, -2 * q, -4 * q, 24 * q, 2 * q, shirt, -1, 102),       // правая рука
                new Part(4 * q, 12 * q, -2 * q, 8 * q, 24 * q, 2 * q, shirt, 1, 103),          // левая рука
                new Part(-4 * q, 0, -2 * q, 0, 12 * q, 2 * q, 0x3B3B8C, 1, 104),               // правая нога
                new Part(0, 0, -2 * q, 4 * q, 12 * q, 2 * q, 0x3B3B8C, -1, 105),               // левая нога
        };
    }

    static Part p(double x0, double y0, double z0, double x1, double y1, double z1, int col) {
        return new Part(x0, y0, z0, x1, y1, z1, col, 0, 0);
    }

    static Part leg(double x0, double y0, double z0, double x1, double y1, double z1, int col, int grp) {
        return new Part(x0, y0, z0, x1, y1, z1, col, grp, 0);
    }

    static Part tex(double x0, double y0, double z0, double x1, double y1, double z1, int col, int tex) {
        return new Part(x0, y0, z0, x1, y1, z1, col, 0, tex);
    }

    static {
        final int EYE = 0x101018;
        for (int i = 0; i < 4; i++) PLAYER_MODELS[i] = playerModel(SHIRTS[i]);
        MODEL[PLAYER] = PLAYER_MODELS[0];
        // Зомби
        MODEL[ZOMBIE] = new Part[]{
                leg(-0.28, 0, -0.12, -0.03, 0.75, 0.12, 0x35358F, 1),
                leg(0.03, 0, -0.12, 0.28, 0.75, 0.12, 0x35358F, -1),
                p(-0.28, 0.75, -0.14, 0.28, 1.35, 0.14, 0x2B7FB5),
                p(-0.22, 1.35, -0.22, 0.22, 1.8, 0.22, 0x72B05E),
                p(-0.48, 1.1, 0.0, -0.28, 1.3, 0.65, 0x72B05E),
                p(0.28, 1.1, 0.0, 0.48, 1.3, 0.65, 0x72B05E),
                p(-0.15, 1.58, 0.215, -0.07, 1.66, 0.23, EYE),
                p(0.07, 1.58, 0.215, 0.15, 1.66, 0.23, EYE),
        };
        // Свинья
        MODEL[PIG] = new Part[]{
                leg(-0.27, 0, 0.18, -0.12, 0.28, 0.33, 0xE89898, 1),
                leg(0.12, 0, 0.18, 0.27, 0.28, 0.33, 0xE89898, -1),
                leg(-0.27, 0, -0.33, -0.12, 0.28, -0.18, 0xE89898, -1),
                leg(0.12, 0, -0.33, 0.27, 0.28, -0.18, 0xE89898, 1),
                p(-0.3, 0.28, -0.4, 0.3, 0.7, 0.4, 0xF4B0B0),
                p(-0.2, 0.4, 0.4, 0.2, 0.8, 0.7, 0xF4B0B0),
                p(-0.1, 0.47, 0.7, 0.1, 0.6, 0.77, 0xE88888),
                p(-0.16, 0.62, 0.7, -0.11, 0.68, 0.715, EYE),
                p(0.11, 0.62, 0.7, 0.16, 0.68, 0.715, EYE),
        };
        // Овца
        MODEL[SHEEP] = new Part[]{
                leg(-0.24, 0, 0.18, -0.1, 0.32, 0.32, 0xA8997F, 1),
                leg(0.1, 0, 0.18, 0.24, 0.32, 0.32, 0xA8997F, -1),
                leg(-0.24, 0, -0.32, -0.1, 0.32, -0.18, 0xA8997F, -1),
                leg(0.1, 0, -0.32, 0.24, 0.32, -0.18, 0xA8997F, 1),
                tex(-0.32, 0.3, -0.42, 0.32, 0.85, 0.42, 0xF2F2F2, 2),
                p(-0.16, 0.55, 0.4, 0.16, 0.9, 0.7, 0x70604E),
                tex(-0.19, 0.82, 0.38, 0.19, 0.98, 0.64, 0xF2F2F2, 2),
                p(-0.12, 0.7, 0.7, -0.07, 0.76, 0.715, EYE),
                p(0.07, 0.7, 0.7, 0.12, 0.76, 0.715, EYE),
        };
        // Корова
        MODEL[COW] = new Part[]{
                leg(-0.3, 0, 0.28, -0.12, 0.45, 0.46, 0x4E3320, 1),
                leg(0.12, 0, 0.28, 0.3, 0.45, 0.46, 0x4E3320, -1),
                leg(-0.3, 0, -0.46, -0.12, 0.45, -0.28, 0x4E3320, -1),
                leg(0.12, 0, -0.46, 0.3, 0.45, -0.28, 0x4E3320, 1),
                tex(-0.33, 0.45, -0.5, 0.33, 0.95, 0.5, 0x5A3B24, 1),
                p(-0.2, 0.7, 0.5, 0.2, 1.1, 0.82, 0x5A3B24),
                p(-0.14, 0.7, 0.82, 0.14, 0.82, 0.87, 0xD9B8A0),
                p(-0.25, 1.08, 0.55, -0.19, 1.18, 0.62, 0xEEEEDD),
                p(0.19, 1.08, 0.55, 0.25, 1.18, 0.62, 0xEEEEDD),
                p(-0.15, 0.93, 0.82, -0.09, 0.99, 0.835, EYE),
                p(0.09, 0.93, 0.82, 0.15, 0.99, 0.835, EYE),
        };
        // Курица
        MODEL[CHICKEN] = new Part[]{
                leg(-0.09, 0, -0.03, -0.03, 0.22, 0.03, 0xE8B020, 1),
                leg(0.03, 0, -0.03, 0.09, 0.22, 0.03, 0xE8B020, -1),
                p(-0.12, 0.22, -0.2, 0.12, 0.5, 0.17, 0xF5F5F5),
                p(-0.05, 0.4, -0.28, 0.05, 0.58, -0.2, 0xE6E6E6),
                p(-0.15, 0.28, -0.12, -0.12, 0.45, 0.1, 0xE0E0E0),
                p(0.12, 0.28, -0.12, 0.15, 0.45, 0.1, 0xE0E0E0),
                p(-0.08, 0.42, 0.15, 0.08, 0.65, 0.28, 0xF5F5F5),
                p(-0.04, 0.5, 0.28, 0.04, 0.55, 0.36, 0xE8B020),
                p(-0.02, 0.62, 0.18, 0.02, 0.7, 0.26, 0xD02020),
                p(-0.02, 0.42, 0.28, 0.02, 0.5, 0.31, 0xD02020),
                p(-0.06, 0.55, 0.28, -0.04, 0.58, 0.29, EYE),
                p(0.04, 0.55, 0.28, 0.06, 0.58, 0.29, EYE),
        };
        // Житель: коричневая ряса, большой нос
        MODEL[VILLAGER] = new Part[]{
                leg(-0.26, 0, -0.12, -0.02, 0.75, 0.12, 0x5B4026, 1),
                leg(0.02, 0, -0.12, 0.26, 0.75, 0.12, 0x5B4026, -1),
                p(-0.28, 0.65, -0.17, 0.28, 1.4, 0.17, 0x7A5A38),
                p(-0.28, 0.85, -0.18, 0.28, 0.95, 0.18, 0x4E3820),          // пояс
                p(-0.22, 1.4, -0.22, 0.22, 1.8, 0.22, 0xC8967A),
                p(-0.05, 1.46, 0.22, 0.05, 1.62, 0.34, 0xB07F64),           // нос
                p(-0.3, 1.0, 0.1, 0.3, 1.2, 0.2, 0x6A4C2E),                 // скрещённые руки
                p(-0.12, 1.62, 0.215, -0.05, 1.68, 0.23, 0x205020),
                p(0.05, 1.62, 0.215, 0.12, 1.68, 0.23, 0x205020),
                p(-0.23, 1.68, -0.23, 0.23, 1.74, 0.23, 0x4E3820),          // брови-шапка
        };
    }
}

/** Звуки, синтезируемые в коде (чистая Java, без файлов). Смешиваются в одном фоновом потоке. */
final class Audio {
    static final int SR = 22050;

    interface Gen { double at(double t, double u); }

    interface F { double at(double u); }

    static final class Voice {
        short[] d;
        double pos, rate = 1;
        double vol;
    }

    final Map<String, short[][]> bank = new HashMap<>();
    final List<Voice> voices = new ArrayList<>();
    final Random rnd = new Random();
    volatile boolean ready, enabled = true;
    SourceDataLine line;

    void start() {
        Thread t = new Thread(this::run, "audio");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();
    }

    /** name - ключ из банка, dist - расстояние до источника в блоках (0 - рядом), gain - громкость 0..1. */
    void play(String name, double dist, double gain) {
        if (!ready || !enabled || line == null) return;
        short[][] vs = bank.get(name);
        if (vs == null) return;
        double fall = Math.max(0, 1 - dist / 30);
        double vol = gain * fall * fall;
        if (vol < 0.02) return;
        Voice v = new Voice();
        v.d = vs[rnd.nextInt(vs.length)];
        v.vol = vol * 0.7;
        v.rate = name.equals("rain") || name.equals("thunder") ? 1 : 0.92 + rnd.nextDouble() * 0.16;
        synchronized (voices) {
            if (voices.size() < 24) voices.add(v);
        }
    }

    void run() {
        try {
            build();
            AudioFormat fmt = new AudioFormat(SR, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, 3072);
            line.start();
        } catch (Throwable e) {
            line = null;                                       // нет звуковой карты - играем без звука
            return;
        }
        ready = true;
        final int n = 441;                                     // 20 мс
        int[] mix = new int[n];
        byte[] buf = new byte[n * 2];
        while (true) {
            boolean any = false;
            java.util.Arrays.fill(mix, 0);
            synchronized (voices) {
                for (int k = voices.size() - 1; k >= 0; k--) {
                    Voice v = voices.get(k);
                    double p = v.pos;
                    boolean fin = false;
                    for (int i = 0; i < n; i++) {
                        int ip = (int) p;
                        if (ip >= v.d.length) { fin = true; break; }
                        double s0 = v.d[ip], s1 = ip + 1 < v.d.length ? v.d[ip + 1] : 0;
                        mix[i] += (int) ((s0 + (s1 - s0) * (p - ip)) * v.vol);
                        p += v.rate;
                    }
                    v.pos = p;
                    any = true;
                    if (fin || p >= v.d.length) voices.remove(k);
                }
            }
            if (!any) {
                try { Thread.sleep(12); } catch (InterruptedException e) { return; }
                continue;
            }
            for (int i = 0; i < n; i++) {
                int s = (int) (30000 * Math.tanh(mix[i] / 26000.0));
                buf[i * 2] = (byte) s;
                buf[i * 2 + 1] = (byte) (s >> 8);
            }
            line.write(buf, 0, buf.length);
        }
    }

    // ---------- синтез ----------
    static short[] mk(double dur, Gen g) {
        int n = (int) (dur * SR);
        short[] s = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR, u = i / (double) n;
            double fade = Math.min(1, Math.min(i, n - 1 - i) / (SR * 0.004));
            double v = Math.max(-1, Math.min(1, g.at(t, u) * fade));
            s[i] = (short) (v * 28000);
        }
        return s;
    }

    /** Шум через фильтр (a: 1 = белый, мало = глухой) + короткий тон с частотой f0->f1. */
    static short[] hit(double dur, double a, double dec, double tone, double tdec, double f0, double f1, long seed) {
        Random r = new Random(seed);
        double[] st = {0, 0};
        return mk(dur, (t, u) -> {
            st[0] += a * ((r.nextDouble() * 2 - 1) - st[0]);
            double v = st[0] * Math.exp(-u * dec) * 1.3;
            if (tone > 0) {
                st[1] += 2 * Math.PI * (f0 + (f1 - f0) * u) / SR;
                v += Math.sin(st[1]) * Math.exp(-u * tdec) * tone;
            }
            return v * 0.6;
        });
    }

    /** Голос: гармоники основной частоты, усиленные около формантной частоты. */
    static short[] vocal(double dur, F f0, F fc, double bw, F env, double noise, double gain, long seed) {
        Random r = new Random(seed);
        double[] ph = {0};
        return mk(dur, (t, u) -> {
            double f = f0.at(u);
            ph[0] += 2 * Math.PI * f / SR;
            double c = fc.at(u), s = 0;
            for (int k = 1; k <= 14 && k * f < 5000; k++) {
                double x = (k * f - c) / bw;
                s += (Math.exp(-x * x) + 0.05 / k) * Math.sin(k * ph[0]) / Math.sqrt(k);
            }
            double e = env.at(u);
            return (s * 0.45 + (r.nextDouble() * 2 - 1) * noise) * e * gain;
        });
    }

    void add(String name, short[]... v) { bank.put(name, v); }

    void build() {
        build0();
        buildMaterials();
    }

    void build0() {
        add("stepSoft", hit(0.09, 0.22, 7, 0.5, 10, 70, 60, 1), hit(0.09, 0.26, 7, 0.5, 10, 80, 65, 2), hit(0.09, 0.2, 8, 0.45, 10, 65, 55, 3));
        add("stepHard", hit(0.06, 0.7, 11, 0.5, 16, 320, 260, 4), hit(0.06, 0.8, 11, 0.45, 16, 380, 300, 5), hit(0.06, 0.6, 12, 0.5, 16, 280, 230, 6));
        add("stepSand", hit(0.11, 0.5, 8, 0, 1, 1, 1, 7), hit(0.11, 0.55, 9, 0, 1, 1, 1, 8));
        add("breakSoft", hit(0.22, 0.28, 6, 0.3, 14, 110, 70, 9), hit(0.22, 0.3, 6, 0.3, 14, 120, 80, 10));
        add("breakStone", hit(0.2, 1.0, 9, 0.5, 12, 190, 120, 11), hit(0.2, 0.9, 10, 0.5, 12, 210, 130, 12));
        add("breakWood", mk(0.24, (t, u) -> {
            double a = Math.sin(2 * Math.PI * (240 - 100 * u) * t) * Math.exp(-u * 9) * 0.6;
            double b = u > 0.3 ? Math.sin(2 * Math.PI * 190 * t) * Math.exp(-(u - 0.3) * 14) * 0.4 : 0;
            return a + b;
        }));
        add("place", hit(0.08, 0.3, 20, 0.7, 10, 160, 80, 13), hit(0.08, 0.35, 20, 0.7, 10, 180, 90, 14));
        add("tick", hit(0.05, 0.35, 10, 0, 1, 1, 1, 15), hit(0.05, 0.4, 11, 0, 1, 1, 1, 16));
        add("splash", mk(0.5, new Gen() {
            final Random r = new Random(17);
            double lp;

            public double at(double t, double u) {
                lp += 0.12 * ((r.nextDouble() * 2 - 1) - lp);
                double env = (u < 0.08 ? u / 0.08 : Math.exp(-(u - 0.08) * 5));
                return lp * env * 1.6 + Math.sin(2 * Math.PI * (500 + 700 * u) * t) * 0.18 * Math.exp(-u * 8);
            }
        }));
        add("eat", mk(0.32, new Gen() {
            final Random r = new Random(18);
            double lp;

            public double at(double t, double u) {
                lp += 0.4 * ((r.nextDouble() * 2 - 1) - lp);
                return lp * Math.exp(-((u * 3) % 1) * 14) * 0.9;
            }
        }));
        add("craft", mk(0.45, (t, u) -> (Math.sin(2 * Math.PI * 880 * t) + 0.6 * Math.sin(2 * Math.PI * 1318 * t)) * Math.exp(-u * 7) * 0.35
                + (u > 0.12 ? Math.sin(2 * Math.PI * 1318 * t) * Math.exp(-(u - 0.12) * 8) * 0.3 : 0)));
        add("rain", mk(0.6, new Gen() {
            final Random r = new Random(40);
            double lp;

            public double at(double t, double u) {
                lp += 0.45 * ((r.nextDouble() * 2 - 1) - lp);
                double wdw = Math.sin(Math.PI * u);
                return lp * wdw * wdw * 0.5;
            }
        }));
        add("thunder", mk(2.6, new Gen() {
            final Random r = new Random(41);
            double lp;

            public double at(double t, double u) {
                lp += 0.03 * ((r.nextDouble() * 2 - 1) - lp);
                double env = (u < 0.03 ? u / 0.03 : Math.exp(-(u - 0.03) * 2.6)) * (0.7 + 0.3 * Math.sin(u * 60));
                return lp * env * 9;
            }
        }));
        add("click", mk(0.05, (t, u) -> Math.sin(2 * Math.PI * 900 * t) * Math.exp(-u * 8) * 0.5));
        add("pop", mk(0.09, (t, u) -> Math.sin(2 * Math.PI * (500 + 900 * u) * t) * Math.exp(-u * 5) * 0.5));
        add("hurt", vocal(0.3, u -> 210 - 90 * u, u -> 900, 400, u -> (1 - u) * Math.min(1, u * 40), 0.12, 1.0, 20));
        add("zombie", zombie(0.92, 21), zombie(1.08, 22));
        add("zhurt", vocal(0.35, u -> 150 - 70 * u, u -> 700 - 300 * u, 300, u -> Math.pow(1 - u, 0.5) * Math.min(1, u * 30), 0.2, 1.1, 23));
        add("pig", pig(0.92, 24), pig(1.1, 25));
        add("cow", vocal(1.2, u -> 125 + 12 * Math.sin(Math.PI * u), u -> 450 + 150 * u, 220, u -> Math.pow(Math.sin(Math.PI * u), 0.5), 0.05, 1.1, 26),
                vocal(1.0, u -> 140 + 10 * Math.sin(Math.PI * u), u -> 420 + 160 * u, 220, u -> Math.pow(Math.sin(Math.PI * u), 0.5), 0.05, 1.1, 27));
        add("sheep", sheep(1.0, 28), sheep(1.12, 29));
        add("chicken", chicken(1.0, 30), chicken(1.2, 31));
        add("villager", vocal(0.45, u -> 190 + 30 * Math.sin(u * 9) - 40 * u, u -> 1100 - 400 * u, 350,
                u -> Math.sin(Math.PI * u) * (0.7 + 0.3 * Math.sin(u * 40)), 0.06, 1.0, 32),
                vocal(0.4, u -> 170 + 25 * Math.sin(u * 8) - 30 * u, u -> 1000 - 350 * u, 350,
                        u -> Math.sin(Math.PI * u) * (0.7 + 0.3 * Math.sin(u * 36)), 0.06, 1.0, 33));
    }

    static short[] crunch(double dur, double dec, double a, double rate, long seed) {
        Random r = new Random(seed);
        double[] st = {0, 0};
        return mk(dur, (t, u) -> {
            if (r.nextDouble() < rate) st[1] = 1;
            st[1] *= 0.93;
            st[0] += a * ((r.nextDouble() * 2 - 1) - st[0]);
            return st[0] * st[1] * Math.exp(-u * dec) * 1.9;
        });
    }

    static short[] glassHit(double dur, long seed, boolean shatter) {
        Random r = new Random(seed);
        double[] f = {2100 + r.nextInt(900), 3100 + r.nextInt(900), 4300 + r.nextInt(900)};
        double[] st = {0};
        return mk(dur, (t, u) -> {
            double v = 0;
            for (int k = 0; k < 3; k++) v += Math.sin(2 * Math.PI * f[k] * t) * Math.exp(-u * (shatter ? 9 : 14) - k) * 0.22;
            st[0] += 0.7 * ((r.nextDouble() * 2 - 1) - st[0]);
            double burst = shatter ? Math.exp(-u * 18) * (r.nextDouble() < 0.35 ? 1 : 0.3) : Math.exp(-u * 60);
            return v + st[0] * burst * 0.45;
        });
    }

    static short[] metalHit(double dur, double f1, double f2) {
        return mk(dur, (t, u) -> (Math.sin(2 * Math.PI * f1 * t) * 0.5 + Math.sin(2 * Math.PI * f2 * t) * 0.35 + Math.sin(2 * Math.PI * f2 * 2.76 * t) * 0.15) * Math.exp(-u * 9) * 0.6);
    }

    static short[] creak(double dur, double f0, double f1, long seed) {
        Random r = new Random(seed);
        double[] ph = {0, 0};
        return mk(dur, (t, u) -> {
            ph[0] += 2 * Math.PI * (f0 + (f1 - f0) * u) / SR;
            ph[1] += 0.3 * ((r.nextDouble() * 2 - 1) - ph[1]);
            double trem = 0.6 + 0.4 * Math.sin(u * 70);
            double thunk = u > 0.85 ? Math.sin(2 * Math.PI * 90 * t) * Math.exp(-(u - 0.85) * 40) * 1.4 : 0;
            return (Math.sin(ph[0]) * 0.35 + Math.sin(ph[0] * 2.03) * 0.2 + ph[1] * 0.12) * trem * Math.sin(Math.PI * Math.min(1, u / 0.85)) + thunk;
        });
    }

    void buildMaterials() {
        add("step_grass", crunch(0.09, 8, 0.35, 0.5, 101), crunch(0.09, 8, 0.3, 0.5, 102), crunch(0.09, 8, 0.4, 0.5, 103));
        add("step_dirt", bank.get("stepSoft"));
        add("step_stone", bank.get("stepHard"));
        add("step_sand", bank.get("stepSand"));
        add("step_wood", hit(0.07, 0.4, 12, 0.8, 14, 210, 150, 104), hit(0.07, 0.45, 12, 0.8, 14, 190, 140, 105), hit(0.07, 0.4, 12, 0.7, 14, 230, 160, 106));
        add("step_gravel", crunch(0.12, 6, 0.8, 0.45, 107), crunch(0.12, 6, 0.75, 0.5, 108), crunch(0.12, 6, 0.85, 0.4, 109));
        add("step_snow", crunch(0.1, 7, 0.45, 0.3, 110), crunch(0.1, 7, 0.4, 0.35, 111));
        add("step_cloth", hit(0.07, 0.12, 9, 0.15, 12, 90, 70, 112), hit(0.07, 0.14, 9, 0.15, 12, 100, 75, 113));
        add("step_glass", glassHit(0.1, 114, false), glassHit(0.1, 115, false));
        add("step_metal", metalHit(0.09, 700, 1100), metalHit(0.09, 640, 1000));
        add("dig_grass", crunch(0.2, 6, 0.3, 0.35, 120), crunch(0.2, 6, 0.35, 0.35, 121));
        add("dig_dirt", bank.get("breakSoft"));
        add("dig_stone", bank.get("breakStone"));
        add("dig_wood", bank.get("breakWood"));
        add("dig_sand", crunch(0.22, 5, 0.55, 0.3, 122), crunch(0.22, 5, 0.6, 0.3, 123));
        add("dig_gravel", crunch(0.25, 4.5, 0.85, 0.4, 124), crunch(0.25, 4.5, 0.8, 0.4, 125));
        add("dig_snow", crunch(0.22, 5, 0.45, 0.25, 126), crunch(0.22, 5, 0.5, 0.25, 127));
        add("dig_cloth", hit(0.2, 0.12, 6, 0.2, 12, 100, 70, 128), hit(0.2, 0.15, 6, 0.2, 12, 110, 75, 129));
        add("dig_glass", glassHit(0.4, 130, true), glassHit(0.4, 131, true));
        add("dig_metal", metalHit(0.3, 520, 830), metalHit(0.3, 600, 960));
        add("door_open", creak(0.3, 180, 300, 140), creak(0.3, 200, 320, 141));
        add("door_close", creak(0.25, 300, 160, 142), creak(0.25, 320, 170, 143));
        add("chest", creak(0.35, 120, 220, 144));
        add("clink", metalHit(0.2, 1500, 2300), metalHit(0.2, 1300, 2000));
        add("boom", mk(1.2, new Gen() {
            final Random r = new Random(170);
            double lp;

            public double at(double t, double u) {
                lp += 0.06 * ((r.nextDouble() * 2 - 1) - lp);
                return (lp * 8 + Math.sin(2 * Math.PI * (60 - 30 * u) * t) * 0.9) * Math.exp(-u * 4.5) * (u < 0.01 ? u / 0.01 : 1);
            }
        }));
        add("fuse", mk(0.9, new Gen() {
            final Random r = new Random(171);
            double lp;

            public double at(double t, double u) {
                lp += 0.55 * ((r.nextDouble() * 2 - 1) - lp);
                return lp * 0.35 * Math.sin(Math.PI * u);
            }
        }));
        add("crit", hit(0.18, 0.9, 11, 0.7, 9, 900, 1700, 160), hit(0.18, 0.85, 11, 0.7, 9, 800, 1500, 161));
        add("chime", mk(0.9, (t, u) -> {
            double v = 0;
            double[] fr = {784, 988, 1319};
            for (int k = 0; k < 3; k++) { double tt = t - k * 0.12; if (tt > 0) v += Math.sin(2 * Math.PI * fr[k] * tt) * Math.exp(-tt * 6) * 0.35; }
            return v;
        }));
        add("portal", mk(1.1, new Gen() {
            final Random r = new Random(150);
            double lp;

            public double at(double t, double u) {
                lp += (0.05 + 0.25 * u) * ((r.nextDouble() * 2 - 1) - lp);
                double env = Math.sin(Math.PI * u);
                return (lp * 2.2 + Math.sin(2 * Math.PI * (110 + 60 * Math.sin(u * 14)) * t) * 0.35) * env * env * 0.7;
            }
        }));
    }

    static short[] zombie(double p, long seed) {
        return vocal(0.9, u -> (85 + 12 * Math.sin(u * 28) - 18 * u) * p, u -> 500 + 200 * Math.sin(u * 9), 260,
                u -> Math.pow(Math.sin(Math.PI * u), 0.6), 0.10, 0.8, seed);
    }

    static short[] pig(double p, long seed) {
        return vocal(0.5, u -> (260 - 90 * Math.min(1, (u % 0.5) / 0.45)) * p, u -> 900, 350,
                u -> { double lu = (u % 0.5) / 0.45; return lu > 1 ? 0 : Math.sin(Math.PI * lu); }, 0.18, 1.0, seed);
    }

    static short[] sheep(double p, long seed) {
        return vocal(0.7, u -> (330 + 25 * u) * p, u -> 1200, 500,
                u -> Math.pow(Math.sin(Math.PI * u), 0.5) * (0.65 + 0.35 * Math.sin(u * 0.7 * 2 * Math.PI * 26)), 0.15, 0.9, seed);
    }

    static short[] chicken(double p, long seed) {
        return vocal(0.25, u -> (750 + 300 * Math.sin(u * Math.PI * 2)) * p, u -> 1600, 700,
                u -> u < 0.9 ? Math.sin(Math.PI * ((u * 3) % 1)) : 0, 0.1, 0.9, seed);
    }
}




















/** Сетевой слой: TCP, пакеты с длиной, отдельные потоки чтения и записи. Логика игры - в MiniCraft. */
final class Net {
    static final int PORT = 25570, VERSION = 7;
    static final int MAX_FRAME = 16 * 1024 * 1024;

    // клиент -> сервер
    static final byte C_HELLO = 1, C_STATE = 2, C_BLOCK = 3, C_ATTACK = 4, C_CHAT = 5, C_DROP = 6,
            C_INV = 7, C_SPILL = 8, C_SLEEP = 9, C_TILE = 10, C_HIT = 11, C_SKIN = 12;
    // сервер -> клиент
    static final byte S_WELCOME = 21, S_PLAYERS = 22, S_MOBS = 23, S_BLOCK = 24, S_CHAT = 25, S_TIME = 26,
            S_HURT = 27, S_LOOT = 28, S_KICK = 29, S_DROPS = 30,
            S_INV = 31, S_TILE = 32, S_FLAGS = 33, S_SLEEP = 34, S_TP = 35, S_SKIN = 36;

    interface Handler {
        /** Вызывается из потока чтения. data[0] - тип пакета. */
        void onPacket(Conn c, byte[] data);

        void onClosed(Conn c, String why);
    }

    static final class Conn {
        final Socket sock;
        final DataInputStream in;
        final DataOutputStream out;
        final LinkedBlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
        volatile boolean open = true;
        // данные игрока (на стороне сервера)
        int id;
        String name = "", uid = "";
        double x, y, z, yaw, pitch;
        boolean ready, sleeping, creator;

        Conn(Socket s) throws IOException {
            sock = s;
            sock.setTcpNoDelay(true);
            in = new DataInputStream(sock.getInputStream());
            out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream(), 1 << 16));
        }

        /** Обязательный пакет. */
        void send(byte[] p) { if (open) q.add(p); }

        /** Пакет, который можно потерять, если канал забит (положение игроков, мобы). */
        void sendLossy(byte[] p) { if (open && q.size() < 20) q.add(p); }

        void close() {
            open = false;
            try { sock.close(); } catch (IOException ignored) { }
            q.add(new byte[0]);                                // разбудить поток записи
        }
    }

    /** Запускает потоки чтения и записи для соединения. */
    static void run(Conn c, Handler h) {
        Thread r = new Thread(() -> {
            String why = "соединение закрыто";
            try {
                while (c.open) {
                    int len = c.in.readInt();
                    if (len <= 0 || len > MAX_FRAME) { why = "ошибка протокола"; break; }
                    byte[] data = new byte[len];
                    c.in.readFully(data);
                    h.onPacket(c, data);
                }
            } catch (IOException e) {
                if (!c.open) why = "отключено";
            } catch (RuntimeException e) {
                e.printStackTrace();
                why = "ошибка: " + e;
            }
            boolean was = c.open;
            c.close();
            if (was || true) h.onClosed(c, why);
        }, "net-read");
        r.setDaemon(true);
        r.start();
        Thread w = new Thread(() -> {
            try {
                while (c.open) {
                    byte[] p = c.q.take();
                    if (p.length == 0) continue;
                    c.out.writeInt(p.length);
                    c.out.write(p);
                    if (c.q.isEmpty()) c.out.flush();
                }
            } catch (Exception ignored) {
                c.close();
            }
        }, "net-write");
        w.setDaemon(true);
        w.start();
    }

    static final class Server {
        final List<Conn> conns = new CopyOnWriteArrayList<>();
        ServerSocket ss;
        volatile boolean running;
        int nextId = 1;

        void start(int port, Handler h) throws IOException {
            ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(port));
            running = true;
            Thread t = new Thread(() -> {
                while (running) {
                    try {
                        Socket s = ss.accept();
                        Conn c = new Conn(s);
                        c.id = nextId++;
                        conns.add(c);
                        Net.run(c, new Handler() {
                            public void onPacket(Conn cc, byte[] d) { h.onPacket(cc, d); }

                            public void onClosed(Conn cc, String why) { conns.remove(cc); h.onClosed(cc, why); }
                        });
                    } catch (IOException e) {
                        if (running) e.printStackTrace();
                    }
                }
            }, "net-accept");
            t.setDaemon(true);
            t.start();
        }

        void stop() {
            running = false;
            try { if (ss != null) ss.close(); } catch (IOException ignored) { }
            for (Conn c : conns) c.close();
            conns.clear();
        }

        void broadcast(byte[] p, Conn except) {
            for (Conn c : conns) if (c.ready && c != except) c.send(p);
        }

        void broadcastLossy(byte[] p) {
            for (Conn c : conns) if (c.ready) c.sendLossy(p);
        }
    }

    static Conn connect(String host, int port, int timeoutMs) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), timeoutMs);
        return new Conn(s);
    }

    // ---------- упаковка пакетов ----------
    static final class W {
        final ByteArrayOutputStream bo = new ByteArrayOutputStream();
        final DataOutputStream d = new DataOutputStream(bo);

        W(byte type) {
            try { d.writeByte(type); } catch (IOException ignored) { }
        }

        W i(int v) { try { d.writeInt(v); } catch (IOException ignored) { } return this; }

        W b(int v) { try { d.writeByte(v); } catch (IOException ignored) { } return this; }

        W f(double v) { try { d.writeFloat((float) v); } catch (IOException ignored) { } return this; }

        W s(String v) { try { d.writeUTF(v.length() > 300 ? v.substring(0, 300) : v); } catch (IOException ignored) { } return this; }

        W raw(byte[] v) { try { d.write(v); } catch (IOException ignored) { } return this; }

        byte[] done() { return bo.toByteArray(); }
    }

    static DataInputStream reader(byte[] data) {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        try { in.readByte(); } catch (IOException ignored) { }       // пропустить тип
        return in;
    }

    /** IPv4-адреса этого компьютера (для подсказки друзьям). */
    static List<String> localAddresses() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e != null && e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> a = ni.getInetAddresses();
                while (a.hasMoreElements()) {
                    InetAddress ia = a.nextElement();
                    if (ia instanceof Inet4Address && !ia.isLinkLocalAddress()) out.add(ia.getHostAddress());
                }
            }
        } catch (Exception ignored) { }
        return out;
    }
}

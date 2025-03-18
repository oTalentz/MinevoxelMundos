package br.com.minevoxel.mundos.utils;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Classe utilitária para gerar nomes aleatórios.
 */
public class NameGenerator {

    private static final String[] PREFIXES = {
            "sky", "cloud", "sun", "moon", "star", "dawn", "dusk", "night", "day",
            "fire", "water", "earth", "wind", "ocean", "mountain", "forest", "meadow",
            "crystal", "shadow", "light", "dark", "bright", "ancient", "mystic", "magic",
            "wild", "calm", "peaceful", "serene", "quiet", "loud", "thunder", "lightning"
    };

    private static final String[] SUFFIXES = {
            "realm", "land", "world", "isle", "island", "haven", "sanctuary", "home",
            "domain", "kingdom", "empire", "republic", "nation", "state", "territory",
            "valley", "peak", "ridge", "canyon", "gorge", "river", "lake", "sea",
            "field", "plain", "desert", "oasis", "forest", "jungle", "tundra", "garden"
    };

    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new SecureRandom();

    /**
     * Gera um nome aleatório com o tamanho especificado.
     *
     * @param length Tamanho do nome aleatório
     * @return String com caracteres aleatórios
     */
    public static String generateName(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("O comprimento deve ser maior que zero");
        }

        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int randomIndex = RANDOM.nextInt(CHARS.length());
            sb.append(CHARS.charAt(randomIndex));
        }

        return sb.toString();
    }

    /**
     * Gera um nome aleatório com prefixo.
     *
     * @param prefix Prefixo a ser adicionado
     * @param length Tamanho da parte aleatória
     * @return String com prefixo seguida de caracteres aleatórios
     */
    public static String generateNameWithPrefix(String prefix, int length) {
        return prefix + generateName(length);
    }

    /**
     * Gera um nome de mundo aleatório usando combinações de prefixo e sufixo.
     *
     * @return Nome de mundo aleatório
     */
    public static String generateWorldName() {
        String prefix = PREFIXES[RANDOM.nextInt(PREFIXES.length)];
        String suffix = SUFFIXES[RANDOM.nextInt(SUFFIXES.length)];

        return prefix + suffix;
    }

    /**
     * Gera um nome de mundo aleatório com um número no final.
     *
     * @return Nome de mundo aleatório com número
     */
    public static String generateWorldNameWithNumber() {
        String prefix = PREFIXES[RANDOM.nextInt(PREFIXES.length)];
        String suffix = SUFFIXES[RANDOM.nextInt(SUFFIXES.length)];
        int number = RANDOM.nextInt(1000);

        return prefix + suffix + number;
    }
}
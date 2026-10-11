package br.com.conde.lsm;

import java.util.List;

/**
 * O FILTRO DE BLOOM: um resumo que responde "com certeza nao" ou "talvez sim".
 *
 * <p>Ele existe por causa de uma assimetria. Numa arvore de mesclagem, achar uma chave que
 * EXISTE custa olhar os arquivos ate ela aparecer. Descobrir que uma chave NAO existe
 * custa olhar TODOS, ate o ultimo, para ter certeza.
 *
 * <p>O filtro corta isso. Cada arquivo guarda alguns bits por chave, e uma consulta que
 * da negativo descarta o arquivo inteiro sem busca binaria nenhuma.
 *
 * <p>Ele erra num sentido so, e esse e o ponto. Dizer "talvez sim" sobre uma chave que nao
 * esta ali custa uma busca em vao. Dizer "nao" sobre uma chave que esta ali seria perder
 * um dado, e isso ele nao faz nunca.
 */
public final class Bloom {

    /** Quantos bits por chave. Dez da uns um por cento de falso positivo com sete funcoes. */
    public static final int BITS_POR_CHAVE = 10;

    /** Quantas posicoes cada chave marca. */
    public static final int FUNCOES = 7;

    private final long[] bits;
    private final int tamanho;

    private Bloom(long[] bits, int tamanho) {
        this.bits = bits;
        this.tamanho = tamanho;
    }

    public static Bloom de(List<String> chaves) {
        int tamanho = Math.max(64, BITS_POR_CHAVE * Math.max(1, chaves.size()));
        long[] bits = new long[(tamanho + 63) / 64];
        Bloom filtro = new Bloom(bits, tamanho);

        for (String chave : chaves) filtro.poe(chave);

        return filtro;
    }

    private void poe(String chave) {
        long resumo = mistura(chave);
        int primeira = (int) (resumo >>> 32);
        int segunda = (int) resumo;

        for (int i = 0; i < FUNCOES; i++) {
            int posicao = Math.floorMod(primeira + i * segunda, tamanho);

            bits[posicao >>> 6] |= 1L << (posicao & 63);
        }
    }

    /** Pode ser que a chave esteja aqui? Um "nao" e definitivo; um "sim" e um talvez. */
    public boolean talvezTenha(String chave) {
        long resumo = mistura(chave);
        int primeira = (int) (resumo >>> 32);
        int segunda = (int) resumo;

        for (int i = 0; i < FUNCOES; i++) {
            int posicao = Math.floorMod(primeira + i * segunda, tamanho);

            if ((bits[posicao >>> 6] & 1L << (posicao & 63)) == 0) return false;
        }

        return true;
    }

    /** Quantos bytes o filtro ocupa. */
    public long bytes() {
        return 8L * bits.length;
    }

    public int tamanho() {
        return tamanho;
    }

    /**
     * A MISTURA FINAL DO SPLITMIX64 sobre o resumo da cadeia.
     *
     * <p>O {@code hashCode} de uma cadeia do Java espalha mal nos bits de cima, e o filtro
     * usa os bits de cima e os de baixo como duas funcoes diferentes. Sem a mistura, as
     * duas sairiam correlacionadas e o filtro erraria muito mais do que promete.
     */
    static long mistura(String chave) {
        long x = chave.hashCode() * 0x9E3779B97F4A7C15L;

        x = (x ^ x >>> 30) * 0xBF58476D1CE4E5B9L;
        x = (x ^ x >>> 27) * 0x94D049BB133111EBL;

        return x ^ x >>> 31;
    }
}

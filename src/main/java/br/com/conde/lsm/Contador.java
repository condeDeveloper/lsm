package br.com.conde.lsm;

/**
 * O CONTADOR das TRES AMPLIFICACOES, que sao o assunto inteiro.
 *
 * <p>Uma arvore de mesclagem escreve so no fim e nunca por cima, e isso e barato. O preco
 * e que o mesmo dado passa a existir em varios lugares, e desse preco saem tres numeros
 * que nao da para melhorar todos ao mesmo tempo:
 *
 * <ul>
 *   <li>ESCRITA: quantos bytes foram parar no disco para cada byte que o usuario mandou
 *       gravar. Compactar e reescrever.
 *   <li>LEITURA: quantos arquivos uma busca precisa olhar. Um dado pode estar em
 *       qualquer nivel.
 *   <li>ESPACO: quantos bytes o disco ocupa para cada byte que ainda vale. Valores
 *       velhos e lapides ficam la ate alguem compactar.
 * </ul>
 *
 * <p>Byte e arquivo, nunca segundo. Segundo muda com a maquina e com o disco, e a
 * integracao continua roda em tres sistemas.
 */
public final class Contador {

    private long bytesEscritos;
    private long bytesDoUsuario;
    private long arquivosOlhados;
    private long descartesPelaFaixa;
    private long consultasAoFiltro;
    private long descartesPeloFiltro;
    private long buscasDentro;
    private long buscasEmVao;
    private long comparacoes;
    private long compactacoes;
    private long leituras;

    /** Bytes que foram parar no disco, contando compactacao. */
    public void escreveu(long bytes) {
        bytesEscritos += bytes;
    }

    /** Bytes que o usuario mandou gravar, contando so a chamada dele. */
    public void usuarioGravou(long bytes) {
        bytesDoUsuario += bytes;
    }

    public void olhouArquivo() {
        arquivosOlhados++;
    }

    public void descartouPelaFaixa() {
        descartesPelaFaixa++;
    }

    public void consultouFiltro() {
        consultasAoFiltro++;
    }

    public void descartouPeloFiltro() {
        descartesPeloFiltro++;
    }

    /** O arquivo nao foi descartado e a busca binaria rodou. */
    public void buscouDentro() {
        buscasDentro++;
    }

    /**
     * A busca binaria rodou e nao achou nada.
     *
     * <p>E este o trabalho desperdicado que o filtro de Bloom existe para evitar, e e por
     * isso que ele e contado separado.
     */
    public void buscaEmVao() {
        buscasEmVao++;
    }

    public void comparou() {
        comparacoes++;
    }

    public void compactou() {
        compactacoes++;
    }

    public void leu() {
        leituras++;
    }

    public long bytesEscritos() {
        return bytesEscritos;
    }

    public long bytesDoUsuario() {
        return bytesDoUsuario;
    }

    /** Quantos bytes o disco levou por byte que o usuario mandou gravar. */
    public double amplificacaoDeEscrita() {
        return bytesDoUsuario == 0 ? 0 : (double) bytesEscritos / bytesDoUsuario;
    }

    public long arquivosOlhados() {
        return arquivosOlhados;
    }

    public long leituras() {
        return leituras;
    }

    /** Quantos arquivos uma leitura olhou, em media. */
    public double arquivosPorLeitura() {
        return leituras == 0 ? 0 : (double) arquivosOlhados / leituras;
    }

    /** Quantas buscas binarias de verdade uma leitura fez, em media. */
    public double buscasPorLeitura() {
        return leituras == 0 ? 0 : (double) buscasDentro / leituras;
    }

    public long descartesPelaFaixa() {
        return descartesPelaFaixa;
    }

    public long consultasAoFiltro() {
        return consultasAoFiltro;
    }

    public long descartesPeloFiltro() {
        return descartesPeloFiltro;
    }

    public long buscasDentro() {
        return buscasDentro;
    }

    public long buscasEmVao() {
        return buscasEmVao;
    }

    public long comparacoes() {
        return comparacoes;
    }

    public long compactacoes() {
        return compactacoes;
    }

    public void zerar() {
        bytesEscritos = 0;
        bytesDoUsuario = 0;
        arquivosOlhados = 0;
        descartesPelaFaixa = 0;
        consultasAoFiltro = 0;
        descartesPeloFiltro = 0;
        buscasDentro = 0;
        buscasEmVao = 0;
        comparacoes = 0;
        compactacoes = 0;
        leituras = 0;
    }

    @Override
    public String toString() {
        return bytesEscritos + " bytes escritos, " + arquivosOlhados + " arquivos olhados em "
                + leituras + " leituras, " + compactacoes + " compactacoes";
    }
}

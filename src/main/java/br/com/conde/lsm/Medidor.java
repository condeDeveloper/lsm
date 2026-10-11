package br.com.conde.lsm;

import java.util.Locale;

/**
 * O MEDIDOR: imprime as tabelas que o README mostra e que os testes conferem.
 *
 * <p>Toda tabela daqui tem um teste do lado que afirma os mesmos numeros. Texto nao
 * quebra quando deixa de ser verdade; teste quebra.
 */
public final class Medidor {

    private Medidor() {
    }

    public static void main(String[] args) {
        String comando = args.length > 0 ? args[0] : "tudo";

        if (comando.equals("correcao") || comando.equals("tudo")) correcao();
        if (comando.equals("escrita") || comando.equals("tudo")) escrita();
        if (comando.equals("leitura") || comando.equals("tudo")) leitura();
        if (comando.equals("espaco") || comando.equals("tudo")) espaco();
        if (comando.equals("troca") || comando.equals("tudo")) troca();
    }

    private static void correcao() {
        System.out.println();
        System.out.println("TODA LEITURA BATE COM O ESPELHO, conferindo todas as chaves que ja existiram.");
        System.out.println();

        var linhas = Medidas.aCorrecao();
        long erradas = linhas.stream().filter(l -> l.divergencias() > 0).count();

        System.out.printf("  %-20s %-14s %10s %12s %12s %14s %12s %10s%n",
                "roteiro", "politica", "filtro", "operacoes", "leituras", "divergencias",
                "arquivos", "niveis");

        for (var l : linhas)
            System.out.printf("  %-20s %-14s %10s %12d %12d %14d %12d %10d%n",
                    l.roteiro(), l.politica(), l.comFiltro() ? "sim" : "nao",
                    l.operacoes(), l.leituras(), l.divergencias(), l.arquivos(), l.niveis());

        System.out.println();
        System.out.printf("  %d combinacoes, %d com divergencia.%n", linhas.size(), erradas);
        System.out.println();
    }

    private static void escrita() {
        System.out.println();
        System.out.println("A AMPLIFICACAO DE ESCRITA: bytes no disco por byte que o usuario gravou.");
        System.out.println();
        System.out.printf("  %-20s %-14s %18s %18s %16s %14s %10s%n",
                "roteiro", "politica", "bytes do usuario", "bytes no disco", "amplificacao",
                "compactacoes", "niveis");

        for (var l : Medidas.aEscrita())
            System.out.printf(Locale.ROOT, "  %-20s %-14s %18d %18d %16.2f %14d %10d%n",
                    l.roteiro(), l.politica(), l.bytesDoUsuario(), l.bytesNoDisco(),
                    l.amplificacao(), l.compactacoes(), l.niveis());

        System.out.println();
    }

    private static void leitura() {
        System.out.println();
        System.out.println("A AMPLIFICACAO DE LEITURA: quantos arquivos uma busca olha.");
        System.out.println();
        System.out.printf("  %-14s %10s %-14s %10s %22s %22s %16s %22s%n",
                "politica", "filtro", "o que le", "arquivos", "arquivos por leitura",
                "buscas por leitura", "buscas em vao", "descartes pelo filtro");

        for (var l : Medidas.aLeitura())
            System.out.printf(Locale.ROOT, "  %-14s %10s %-14s %10d %22.2f %22.2f %16d %22d%n",
                    l.politica(), l.comFiltro() ? "sim" : "nao", l.oQueLe(), l.arquivos(),
                    l.arquivosPorLeitura(), l.buscasPorLeitura(), l.buscasEmVao(),
                    l.descartesPeloFiltro());

        System.out.println();
    }

    private static void espaco() {
        System.out.println();
        System.out.println("A AMPLIFICACAO DE ESPACO: bytes no disco por byte que ainda vale.");
        System.out.println();
        System.out.printf("  %-20s %-14s %14s %16s %16s %14s %10s %20s%n",
                "roteiro", "politica", "chaves vivas", "bytes vivos", "bytes no disco",
                "amplificacao", "lapides", "entradas no disco");

        for (var l : Medidas.oEspaco())
            System.out.printf(Locale.ROOT, "  %-20s %-14s %14d %16d %16d %14.2f %10d %20d%n",
                    l.roteiro(), l.politica(), l.chavesVivas(), l.bytesVivos(), l.bytesNoDisco(),
                    l.amplificacao(), l.lapides(), l.entradasNoDisco());

        System.out.println();
    }

    private static void troca() {
        System.out.println();
        System.out.println("AS TRES AMPLIFICACOES LADO A LADO, no mesmo roteiro.");
        System.out.println();
        System.out.printf("  %-14s %10s %16s %26s %14s %12s %10s %26s%n",
                "politica", "filtro", "escrita", "leitura de ausentes", "espaco", "arquivos",
                "niveis", "cruzamentos no mesmo nivel");

        for (var l : Medidas.aTroca())
            System.out.printf(Locale.ROOT, "  %-14s %10s %16.2f %26.2f %14.2f %12d %10d %26d%n",
                    l.politica(), l.comFiltro() ? "sim" : "nao", l.escrita(),
                    l.leituraDeAusentes(), l.espaco(), l.arquivos(), l.niveis(), l.cruzamentos());

        System.out.println();
    }
}

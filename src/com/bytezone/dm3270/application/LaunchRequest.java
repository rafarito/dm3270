package com.bytezone.dm3270.application;

import java.util.Optional;

import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.utilities.Site;

/*
 * O que o usuario pediu na janela de abertura, ja lido dos widgets.
 *
 * Ate este commit o Console montava esse pedido sozinho, alcancando dez membros
 * package-private do OptionStage - o ToggleGroup, os tres ComboBox, os dois SiteListStage,
 * a pasta de spy e o item de menu do modo. Eram 100% da superficie de pacote de uma classe
 * que nao tinha acessor nenhum, e trocar um ComboBox por outro controle quebrava o Console.
 *
 * O pedido carrega a REFERENCIA VIVA ao Site, e nunca uma copia dos seus valores. Os
 * acessores de Site nao sao puros: no SiteForm, getPort () e getModel () corrigem o widget
 * quando encontram valor invalido, e e o valor corrigido que o SiteListStage grava nas
 * Preferences. Congelar a porta aqui mudaria o que fica no disco - foi essa propriedade que
 * descartou o record Site que o plano previa para o passo 3.
 *
 * A pasta e o arquivo de replay vem CRUS, e nao como um Path pronto, pelo mesmo motivo de
 * momento: hoje a concatenacao so acontece no ramo do replay, e monta-la aqui a faria rodar
 * nos quatro. O arquivo e a string vazia quando nada foi escolhido, e nao null - o modelo de
 * selecao do ComboBox aceita um item fora da lista, e o caminho resultante e "<pasta>/".
 *
 * O que NAO esta aqui, e por que: a busca de site por nome. O ramo do replay so descobre o
 * nome do servidor depois de carregar a sessao gravada, entao a consulta e tardia por
 * natureza e nao cabe num valor tirado antes do lancamento. Ela virou
 * OptionStage.findServerSite (String), que e o proprio stage respondendo sobre o que ele
 * guarda, em vez de entregar o widget para o chamador perguntar.
 */
// -----------------------------------------------------------------------------------//
record LaunchRequest (TerminalFunction function, Optional<Site> serverSite,
    Optional<Site> clientSite, String spyFolder, String replayFile)
// -----------------------------------------------------------------------------------//
{
}

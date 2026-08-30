package com.bytezone.dm3270.buffers;

/*
 * Um buffer que sabe dizer o proprio nome.
 *
 * Os quatro tipos que implementam isto ja tinham getName () ha muito tempo, cada um por sua
 * conta, sem nada que os ligasse. O SessionRecord, que so quer o nome para mostrar numa coluna
 * da tabela de replay, precisava perguntar de tipo em tipo:
 *
 *     if (message instanceof TelnetCommand)              ... getName ()
 *     else if (message instanceof TelnetSubcommand)      ... getName ()
 *     else if (message instanceof Command)               ... getName ()
 *     else if (message instanceof TN3270ExtendedCommand) ... getName ()
 *     else if (message instanceof AbstractExtendedCommand) ... getName ()
 *
 * O comentario que estava logo acima dessa cadeia - "create Interface (Identifiable?) with
 * getName()" - descrevia exatamente esta interface.
 *
 * QUEM NAO ESTA AQUI, e de proposito: o CommandHeader. Ele desce de AbstractReplyBuffer como os
 * outros, mas nunca teve getName () e nao casava com nenhum ramo da cadeia - um SessionRecord
 * feito dele ficava sem nome. Declarar isto na superclasse comum obrigaria o CommandHeader a
 * inventar um nome, que e mudanca de comportamento. Ele continua sem nome.
 *
 * Pelo mesmo motivo a interface nao entrou em AbstractTN3270Command: de la descem Command e
 * StructuredField, e so o primeiro era nomeado.
 */
// -----------------------------------------------------------------------------------//
public interface NamedBuffer extends Buffer
// -----------------------------------------------------------------------------------//
{
  String getName ();
}

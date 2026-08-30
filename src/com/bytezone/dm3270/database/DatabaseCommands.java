package com.bytezone.dm3270.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.database.DatabaseRequest.Result;

/*
 * Os quatro comandos que operam sobre o banco inteiro, e nao sobre um dataset ou um membro:
 * abrir, criar, derrubar e fechar.
 *
 * O CLOSE nao e uma operacao de banco - e um pedido para a thread parar de atender a fila. Por
 * isso quem constroi esta classe entrega um Runnable, em vez de ela conhecer a thread: a
 * responsabilidade de parar continua sendo de quem roda o laco.
 *
 * O logger e o do DatabaseThread. O padrao do logback imprime %logger{36}, entao o nome da
 * classe faz parte de toda linha - um logger proprio mudaria saida observavel. O aviso do
 * ramo default mantem o erro de digitacao de origem, "Unnown", pelo mesmo motivo.
 */
// -----------------------------------------------------------------------------------//
final class DatabaseCommands
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DatabaseThread.class);

  private final SchemaInitializer schema;
  private final DatasetCache cache;
  private final Runnable onClose;

  // ---------------------------------------------------------------------------------//
  DatabaseCommands (SchemaInitializer schema, DatasetCache cache, Runnable onClose)
  // ---------------------------------------------------------------------------------//
  {
    this.schema = schema;
    this.cache = cache;
    this.onClose = onClose;
  }

  // ---------------------------------------------------------------------------------//
  void execute (DatabaseRequest request)
  // ---------------------------------------------------------------------------------//
  {
    switch (request.command)
    {
      case OPEN:
        if (schema.create ())                       // create if not already there
          request.result = Result.SUCCESS;
        break;

      case DROP:
        if (dropTables ())
          request.result = Result.SUCCESS;
        break;

      case CREATE:
        if (dropTables () && schema.create ())
          request.result = Result.SUCCESS;
        break;

      case CLOSE:
        onClose.run ();
        request.result = Result.SUCCESS;
        break;

      default:
        logger.warn ("Unnown database request: {}", request);
        break;
    }
  }

  /*
   * Derrubar as tabelas limpa o cache junto, e so quando o drop deu certo - como antes. O
   * cache indexa o que o banco tinha; apagar um sem o outro deixaria os dois em desacordo.
   */
  // ---------------------------------------------------------------------------------//
  private boolean dropTables ()
  // ---------------------------------------------------------------------------------//
  {
    if (!schema.drop ())
      return false;

    cache.clear ();
    return true;
  }
}

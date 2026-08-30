package com.bytezone.dm3270.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;

import com.bytezone.dm3270.database.DatabaseRequest.Result;

/*
 * A thread que atende a fila de requisicoes do banco.
 *
 * Sobrou com tres coisas: abrir e fechar a conexao SQLite, montar o grafo de colaboradores, e
 * tirar requisicoes da fila para entregar a quem sabe atende-las. Tudo o mais - o esquema, o
 * SQL das duas tabelas, o cache e os tres conjuntos de comandos - mora em classe propria.
 *
 * A cadeia de instanceof do laco ficou de proposito. A alternativa seria um Visitor, com um
 * execute () declarado em cada tipo de requisicao, e ele removeria os tres ramos ao custo de
 * por comportamento nas requisicoes - que sao dados mutaveis atravessando fronteira de thread
 * sem sincronizacao (item 7 do BACKLOG-DEFEITOS.md). Manter as requisicoes como dado puro, e o
 * despacho fora delas, e a forma mais segura enquanto esse defeito existir. Sao tres ramos
 * sobre uma hierarquia fechada, e eles cabem em cinco linhas.
 */
// -----------------------------------------------------------------------------------//
public class DatabaseThread extends Thread
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DatabaseThread.class);

  private Connection connection;
  private BlockingQueue<DatabaseRequest> queue;
  private boolean cancelled;
  private final String databaseName;

  private final DatabaseCommands databaseCommands;
  private final DatasetCommands datasetCommands;
  private final MemberCommands memberCommands;

  // ---------------------------------------------------------------------------------//
  public DatabaseThread (String databaseName, BlockingQueue<DatabaseRequest> queue)
  // ---------------------------------------------------------------------------------//
  {
    this.databaseName = databaseName;
    this.queue = queue;

    try
    {
      Class.forName ("org.sqlite.JDBC");     // add sqlite JDBC Driver to DriverManager
      Path path = Paths.get (System.getProperty ("user.home"), "dm3270", "databases",
          databaseName);
      Files.createDirectories (path.getParent ());
      String connectionName = "jdbc:sqlite:" + path.toString ();
      connection = DriverManager.getConnection (connectionName);
      connection.setAutoCommit (true);
    }
    catch (Exception e)
    {
      cancelled = true;
      logger.error ("Error initializing database connection", e);
    }

    /*
     * O grafo e montado mesmo quando a conexao falhou, e com ela nula: o laco nao chega a
     * rodar nesse caso, porque cancelled ja esta marcado. Ninguem toca na conexao aqui.
     *
     * O CLOSE precisa parar a thread, e nao o banco. Em vez de dar a thread ao
     * DatabaseCommands, ele recebe o que fazer - e a responsabilidade de parar continua de
     * quem roda o laco.
     */
    DatasetCache cache = new DatasetCache ();
    DatasetRepository datasets = new DatasetRepository (connection, cache);
    MemberRepository members = new MemberRepository (connection, cache, datasets);

    databaseCommands = new DatabaseCommands (new SchemaInitializer (connection), cache,
        () -> cancelled = true);
    datasetCommands = new DatasetCommands (datasets, cache);
    memberCommands = new MemberCommands (members, datasets, cache);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void run ()
  // ---------------------------------------------------------------------------------//
  {
    while (!cancelled)
    {
      try
      {
        DatabaseRequest request = queue.take ();

        request.result = Result.FAILURE;
        request.databaseName = databaseName;

        if (request instanceof DatasetRequest)
          datasetCommands.execute ((DatasetRequest) request);
        else if (request instanceof MemberRequest)
          memberCommands.execute ((MemberRequest) request);
        else
          databaseCommands.execute (request);

        request.initiator.processResult (request);
      }
      catch (InterruptedException e)
      {
        logger.info ("interrupted");
        cancelled = true;
        Thread.currentThread ().interrupt ();     // preserve the message
      }
    }

    try
    {
      connection.close ();
      logger.info ("Connection closed");
    }
    catch (SQLException e)
    {
      logger.error ("Error closing connection", e);
    }
  }
}

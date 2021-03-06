// Copyright (C) king.com Ltd 2016
// https://github.com/king/scylla
// License: Apache 2.0, https://raw.github.com/king/scylla/LICENSE-APACHE

package com.king.scylla.connectors;

import com.king.scylla.Answer;
import com.king.scylla.VerificationAnswer;
import com.king.scylla.meta.QConfig;
import com.king.scylla.meta.ScyllaException;
import org.json.JSONException;

import java.io.IOException;
import java.sql.*;

import static com.king.scylla.VerificationAnswer.emptyVerificationAnswer;

public class DBConnector {
    final QConfig qc;
    private final String connectorClass;
    private final String connectionString;

    public Connection getConnection() throws SQLException, ClassNotFoundException {
        Class.forName(connectorClass);

        return DriverManager.getConnection(connectionString, qc.getUser(), qc.getPassword());
    }

    public VerificationAnswer verifyQuery() throws SQLException, JSONException {
        try {
            Connection conn = getConnection();

            VerificationAnswer answer = emptyVerificationAnswer();
            try {
                Statement stmt = conn.createStatement();
                if (qc.getHParams() != null) {
                    for (String param : qc.getHParams()) {
                        stmt.execute(param);
                    }
                }

                PreparedStatement pstmt = conn.prepareStatement(qc.getQuery());
                pstmt.close();
                answer.ok(true);
            } catch (SQLException e) {
                answer.ok(false).err(e.getMessage());
            }
            conn.close();
            return answer;
        } catch (ClassNotFoundException e) {
            throw new SQLException(qc.getScope().classNotFound());
        }
    }

    private Answer getAnswerFromStatement(Statement stmt, boolean update)
            throws SQLException, IOException, ScyllaException {
        if (update) {
            int n = stmt.executeUpdate(qc.getQuery());
            return Answer.answerForUpdateStatement(n);
        } else {
            ResultSet rs = stmt.executeQuery(qc.getQuery());
            return Answer.answerFromResultSet(qc, rs);
        }
    }

    public Answer query(boolean update) throws SQLException, JSONException, IOException, ScyllaException {
        try {
            Connection conn = getConnection();
            assert !conn.isClosed();

            Statement stmt = conn.createStatement();

            if (qc.getHParams() != null) {
                for (String param : qc.getHParams()) {
                    stmt.execute(param);
                }
            }

            Answer answer = getAnswerFromStatement(stmt, update);

            stmt.close();
            conn.close();

            return answer;
        } catch (ClassNotFoundException e) {
            throw new SQLException(qc.getScope().classNotFound());
        }
    }

    DBConnector(QConfig qc, String connectorClass, String connectionString) {
        this.qc = qc;
        this.connectorClass = connectorClass;
        this.connectionString = connectionString;
    }

    public DBConnector(QConfig qc) {
        this.qc = qc;
        this.connectionString = qc.getConf().getJDBCStringForSope(qc.getScope());
        this.connectorClass = qc.getScope().getConnectorClass();
    }
}

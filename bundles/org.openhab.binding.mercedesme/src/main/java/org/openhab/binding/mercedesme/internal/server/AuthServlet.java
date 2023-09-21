/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mercedesme.internal.server;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.mercedesme.internal.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link AuthServlet} class provides authentication callback endpoint
 *
 * @author Bernd Weymann - Initial contribution
 */
@SuppressWarnings("serial")
@NonNullByDefault
public class AuthServlet extends HttpServlet {
    private final Logger logger = LoggerFactory.getLogger(AuthServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthService myAuthService = AuthService.getAuthService(request.getLocalPort());
        AuthServer myServer = AuthServer.getServer(request.getLocalPort());
        HttpClient client = myServer.getHttpClient();
        String guid = request.getParameter(Constants.GUID);
        String pin = request.getParameter(Constants.PIN);
        if (guid == null && pin == null) {
            // request PIN
            String requestVal = myAuthService.requestPin();
            if (!requestVal.equals(Constants.NOT_SET)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println("<HTML>");
                response.getWriter().println("<BODY>");
                response.getWriter().println("<H1>Step 1 - PIN Requested</H1>");
                response.getWriter().println("<BR>");
                response.getWriter().println("PIN was requested and should be avialble in your EMail Inbox<BR>");
                response.getWriter()
                        .println("Check first if you received the PIN and then continue with the below Link<BR>");
                response.getWriter().println("<a href=\"" + Constants.CALLBACK_ENDPOINT + "?guid=" + requestVal
                        + "\">Click here to continue with Step 2</a>");
                response.getWriter().println("</BODY>");
                response.getWriter().println("</HTML>");
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println("<HTML>");
                response.getWriter().println("<BODY>");
                response.getWriter().println("Something went wrong<BR>");
                response.getWriter().println("</BODY>");
                response.getWriter().println("</HTML>");
            }

        } else if (guid != null && pin == null) {
            // show instert PIN input field

            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println("<HTML>");
            response.getWriter().println("<BODY>");
            response.getWriter().println("<H1>Step 2 - Enter PIN</H1>");
            response.getWriter().println("<BR>");
            response.getWriter().println("Enter PIN in second input field - leave guid as it is!<BR>");
            response.getWriter().println("<form action=\"" + Constants.CALLBACK_ENDPOINT + "\">");
            response.getWriter().println("<BR>");
            response.getWriter().println("<label for=\"GUID\">GUID</label>");
            response.getWriter().println("<input type=\"text\" id=\"guid\" name=\"guid\" value=\"" + guid + "\">");
            response.getWriter().println("<BR>");
            response.getWriter().println("<label for=\"PIN\">PIN</label>");
            response.getWriter().println("<input type=\"text\" id=\"pin\" name=\"pin\" placeholder=\"Your PIN\">");
            response.getWriter().println("<BR>");
            response.getWriter().println("<input type=\"submit\" value=\"Submit\">");
            response.getWriter().println("</form>");
            response.getWriter().println("</BODY>");
            response.getWriter().println("</HTML>");
        } else if (guid != null && pin != null) {
            // call getToken and show result
            boolean result = myAuthService.requestToken(guid + ":" + pin);
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println("<HTML>");
            response.getWriter().println("<BODY>");
            response.getWriter().println("<H1>Step 3 - Save Token</H1>");
            response.getWriter().println("<BR>");
            if (result) {
                response.getWriter().println("Success - everything done!<BR>");
            } else {
                response.getWriter().println("Failure - Please check logs for further analysis!<BR>");
            }
            response.getWriter().println("</BODY>");
            response.getWriter().println("</HTML>");
        }
        logger.debug("Call from {}:{} parameters {}", request.getLocalAddr(), request.getLocalPort(),
                request.getParameterMap());
    }
}

/*
 * Copyright (c) 2020.
 *
 * This file is part of Hypixel Skyblock Assistant.
 *
 * Hypixel Guild Synchronizer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hypixel Guild Synchronizer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hypixel Guild Synchronizer.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.senither.hypixel.servlet.handlers;

import com.senither.hypixel.servlet.WebServlet;
import org.apache.commons.lang.exception.ExceptionUtils;
import spark.ExceptionHandler;
import spark.Request;
import spark.Response;

public class SparkExceptionHandler implements ExceptionHandler<Exception> {

    @Override
    public void handle(Exception exception, Request request, Response response) {
        WebServlet.log.error(request.requestMethod() + " " + request.pathInfo(), exception);

        response.body(ExceptionUtils.getStackTrace(exception));
        response.type("text/plain");
        response.status(500);
    }
}

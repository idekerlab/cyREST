package org.cytoscape.rest.internal.model;

import io.swagger.annotations.ApiModel;

/**
 * Message model for returning messages from REST calls.
 * 
 * @author David Otasek (dotasek.dev@gmail.com)
 *
 */
@ApiModel
public class Message 
{
	public String message;
	
	public Message(String string) {
		message = string;
	}
}

package hu;

import java.util.List;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;

class Info {
	public final Message msg;
	public final Terser t;
	public final Pos pos;
	public final TP tp;
	public final List<VE> errors;
	
	public Info (String error) {
		this.msg = null;
		this.t = null;
		this.pos = null;
		this.tp = new TP(error, "", "");
		this.errors = null;
	}

	public Info (Message msg, Terser t, Pos pos, TP tp, List<VE> errors) {
		this.msg = msg;
		this.t = t;
		this.pos = pos;
		this.tp = tp;
		this.errors = errors;
	}
}
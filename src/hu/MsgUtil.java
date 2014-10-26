package hu;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.*;

import ca.uhn.hl7v2.*;
import ca.uhn.hl7v2.model.*;
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;

/** utilities for hl7 messages */
public class MsgUtil {
	
	private MsgUtil () {
		//
	}
	
	// XXX
	public static String toMsgCr (String msgLf) {
		if (msgLf.length() > 4) {
			String segmentSep = msgLf.substring(3, 4);
			return msgLf.replaceAll("\n(^|[A-Z][A-Z0-9]{2}" + Pattern.quote(segmentSep) + ")", "\r$1");
			
		} else {
			return msgLf.replace('\n', Sep.SEGMENT);
		}
	}
	
	/** get info about the message and the index */
	public static Info getInfo (final String msgLf, final String version) throws Exception {
		// parse the message
		final String msgCr = msgLf.replace('\n', Sep.SEGMENT);
		// XXX closing this causes a null pointer exception
		final HapiContext context = new DefaultHapiContext();
		
		if (!version.equals(EditorJFrame.AUTO_VERSION)) {
			CanonicalModelClassFactory mcf = new CanonicalModelClassFactory(version);
			context.setModelClassFactory(mcf);
		}
		
		PipeParser p = context.getPipeParser();
		p.setValidationContext(ValidationContextFactory.noValidation());
		final Message msg = p.parse(msgCr);
		
		final Terser terser = new Terser(msg);
		
		// do the validations
		final Sep sep = new Sep(msg);
		
		// TODO validate missing/unexpected segments?
		for (String s : msg.getNames()) {
			System.out.println("name=" + s + " required=" + msg.isRequired(s) + " group=" + msg.isGroup(s) + " repeating=" + msg.isRepeating(s));
		}
		
		return new Info(msg, terser, sep, msgCr);
	}
	
	public static List<ValidationMessage> getErrors (Message msg, String msgCr, Sep sep, String msgVersion, String selectedValue) throws Exception {
		if (msgVersion.equals(EditorJFrame.AUTO_VERSION)) {
			msgVersion = msg.getVersion();
		}
		ValidatingMessageVisitor vmv = new ValidatingMessageVisitor(msgCr, sep, msgVersion, selectedValue);
		MessageVisitors.visit(msg, vmv);
		return vmv.getErrors();
	}
	
	/** get the terser path for the message position */
	public static TP getTerserPath (final Message msg, final Terser t, final Pos pos) throws Exception {
		SL sl = getSegmentLocation(msg, pos.segOrd);
		StringBuilder pathSb = new StringBuilder();
		String desc = "";
		String value = "";
		String path = "";
		
		if (sl != null) {
			pathSb.append(sl.location.toString());
			if (pos.fieldOrd > 0) {
				pathSb.append("-" + pos.fieldOrd);
				if (pos.fieldRep > 0) {
					pathSb.append("(" + pos.fieldRep + ")");
				}
				if (pos.compOrd > 1 || pos.subCompOrd > 1) {
					pathSb.append("-" + pos.compOrd);
					if (pos.subCompOrd > 1) {
						pathSb.append("-" + pos.subCompOrd);
					}
				}
			}
			path = pathSb.toString();
			desc = "Message " + msg.getName() + ", " + getDescription(sl.segment, pos);
			
			if (pos.fieldOrd > 0) {
				try {
					value = t.get(path);
					
				} catch (Exception e) {
					System.out.println("could not get value of terser path: " + e);
					desc = e.toString();
				}
			}
		}
		
		return new TP(path, value, desc);
	}
	
	/** get position of segment path */
	public static Pos getPosition (Message msg, Terser terser, String path) throws Exception {
		Segment segment = terser.getSegment(path.substring(0, path.indexOf("-")));
		int[] i = Terser.getIndices(path);
		int s = getSegmentOrdinal(msg, segment);
		return new Pos(s, i[0], i[1], i[2], i[3]);
	}
	
	/** get segment ordinal of segment */
	public static int getSegmentOrdinal (final Message msg, final Segment segment) {
		final int[] segOrd = new int[1];
		
		final MessageVisitorAdapter mv = new MessageVisitorAdapter() {
			int s = 1;
			@Override
			public boolean start2 (Segment segment2, Location location) throws HL7Exception {
				if (segment == segment2) {
					segOrd[0] = s;
					continue_ = false;
					return false;
				}
				s++;
				return continue_;
			}
		};
		
		try {
			MessageVisitors.visit(msg, MessageVisitors.visitStructures(mv));
			
		} catch (HL7Exception e) {
			System.out.println("could not get segment name: " + e);
		}
		
		return segOrd[0];
	}
	
	/** get segment and segment location from a segment ordinal */
	public static SL getSegmentLocation (final Message msg, final int segmentOrd) {
		final SL[] sl = new SL[1];
		MessageVisitorAdapter mv = new MessageVisitorAdapter() {
			int s = 1;
			@Override
			public boolean start2 (Segment segment, Location location) throws HL7Exception {
				if (s == segmentOrd) {
					sl[0] = new SL(segment, location);
					continue_ = false;
				}
				s++;
				return continue_;
			}
		};
		try {
			MessageVisitors.visit(msg, MessageVisitors.visitStructures(mv));
		} catch (HL7Exception e) {
			System.out.println("could not get segment name: " + e);
		}
		return sl[0];
	}
	
	/** get description of hl7 field, component and subcomponent */
	public static String getDescription (final Segment segment, final Pos pos) {
		System.out.println("get description " + pos);
		StringBuilder sb = new StringBuilder();
		sb.append("segment " + segment.getName());
		Class<?>[] type = new Class[] { segment.getClass() };
		String field = getDescription2(type, pos.fieldOrd);
		if (field != null) {
			sb.append(", field " + field);
			if (type[0] != null) {
				String comp = getDescription2(type, pos.compOrd);
				if (comp != null) {
					sb.append(", component " + comp);
					if (type[0] != null) {
						String subcomp = getDescription2(type, pos.subCompOrd);
						if (subcomp != null) {
							sb.append(", subcomponent " + subcomp);
						} else {
							sb.append(", unknown subcomponent");
						}
					} else if (pos.subCompOrd > 1) {
						sb.append(", unknown subcomponent");
					}
				} else {
					sb.append(", unknown component");
				}
			} else if (pos.compOrd > 1) {
				sb.append(", unknown component");
				
			} else if (pos.subCompOrd > 1) {
				sb.append(", unknown subcomponent");
			}
		} else {
			sb.append(", unknown field");
		}
		return sb.toString();
	}
	
	/** get description of method from class */
	private static String getDescription2 (Class<?>[] type, int ord) {
		Pattern methodPat = Pattern.compile("get\\w{" + type[0].getSimpleName().length() + "}(\\d+)_(\\w+)");
		for (Method m : type[0].getMethods()) {
			Class<?> rt = m.getReturnType();
			if (rt != null && !rt.isPrimitive()) {
				// getObr5_PriorityOBR()
				Matcher mat = methodPat.matcher(m.getName());
				if (mat.matches()) {
					int f = Integer.parseInt(mat.group(1));
					if (f == ord) {
						if (Object[].class.isAssignableFrom(rt)) {
							// remove array indirection
							rt = rt.getComponentType();
						}
						String name = mat.group(2) + " [" + rt.getSimpleName() + "]";
//						if (!AbstractType.class.isAssignableFrom(type)) {
//							System.out.println("name " + name + " is not abstract type");
//							type = null;
//						}
						if (rt != null && AbstractPrimitive.class.isAssignableFrom(rt)) {
							System.out.println("name " + name + " is abstract primitive");
							// can't go deeper
							rt = null;
						}
						type[0] = rt;
						return name;
					}
				}
			}
		}
		return null;
	}
	
	/** get the segment, field etc for the character index in the message */
	public static Pos getPosition (final String msgstrCr, final Sep sep, final int index) {
		if (msgstrCr.contains("\n")) {
			throw new RuntimeException("getPosition requires msgCr");
		}
		// start field at 1 for MSH, 0 for others
		int s = 1, f = 1, fr = 0, c = 1, sc = 1;
		for (int i = 0; i < index; i++) {
			char ch = msgstrCr.charAt(i);
			if (ch == Sep.SEGMENT) {
				s++;
				f = 0;
				fr = 0;
				c = 1;
				sc = 1;
			} else if (ch == sep.field) {
				f++;
				fr = 0;
				c = 1;
				sc = 1;
			} else if (ch == sep.repetition) {
				fr++;
				c = 1;
				sc = 1;
			} else if (ch == sep.component) {
				c++;
				sc = 1;
			} else if (ch == sep.subcomponent) {
				sc++;
			}
		}
		
		return new Pos(s, f, fr, c, sc);
	}
	
	/** get the character indexes (start and end) of the given logical position */
	public static int[] getIndexes (final String msgCr, final Sep sep, final Pos pos) {
		System.out.println("get indexes: " + msgCr.length() + ", " + pos);
		
		if (msgCr.contains("\n")) {
			throw new RuntimeException("getIndex requires msgCr");
		}
		
		final int[] indexes = new int[2];
		
		// field and repetition are prefixed (with ~ and |) so start at 0
		// segment, component and subcomponent may not be prefixed (with CR, ^ or &) so start at 1
		int s = 1, f = 0, r = 0, c = 1, sc = 1, len = 0;
		
		boolean found = false;
		
		for (int i = 0; i < msgCr.length(); i++) {
			char ch = msgCr.charAt(i);
			
			//System.out.println(String.format("getIndexes: ch %x s %d f %d fr %d c %d sc %d len %d", (int) ch, s, f, r, c, sc, len));
			
			if (s == pos.segOrd && f == pos.fieldOrd && r == pos.fieldRep && c == pos.compOrd && sc == pos.subCompOrd) {
				//System.out.println("matched");
				if (!found) {
					indexes[0] = i;
					indexes[1] = i;
					found = true;
					
				} else {
					indexes[1] = indexes[0] + len;
				}
				
			} else if (found || s > pos.segOrd) {
				break;
			}
			
			if (i == 4 && msgCr.startsWith("MSH")) {
				// special case, MSH-2 always begins at index 4
				f++;
				
			} else {
				if (ch == Sep.SEGMENT) {
					s++;
					f = 0;
					r = 0;
					c = 1;
					sc = 1;
					len = 0;
					
				} else if (ch == sep.field) {
					f++;
					r = 0;
					c = 1;
					sc = 1;
					len = 0;
					
				} else if (ch == sep.repetition) {
					r++;
					c = 1;
					sc = 1;
					len = 0;
					
				} else if (ch == sep.component) {
					c++;
					sc = 1;
					len = 0;
					
				} else if (ch == sep.subcomponent) {
					sc++;
					len = 0;
					
				} else {
					len++;
				}
			}
		}
		
		if (found) {
			return indexes;
			
		} else {
			System.out.println("could not find pos");
			return null;
		}
	}
	
}

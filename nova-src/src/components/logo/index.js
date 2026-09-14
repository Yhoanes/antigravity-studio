import "./style.scss";
import tag from "html-tag-js";

export default function Logo() {
	return (
		<div className="nova-logo-container" aria-label="Nova IDE Logo">
			<svg className="nova-logo-svg" viewBox="0 0 512 512" width="100%" height="100%">
				<defs>
					<linearGradient id="uiNovaGrad" x1="0%" y1="0%" x2="100%" y2="100%">
						<stop offset="0%" stop-color="#00f0ff" />
						<stop offset="60%" stop-color="#8b5cf6" />
						<stop offset="100%" stop-color="#3b82f6" />
					</linearGradient>
					<filter id="uiNovaBloom" x="-20%" y="-20%" width="140%" height="140%">
						<feGaussianBlur stdDeviation="8" result="blur" />
						<feMerge>
							<feMergeNode in="blur" />
							<feMergeNode in="SourceGraphic" />
						</feMerge>
					</filter>
				</defs>
				<circle cx="256" cy="256" r="90" fill="#00f0ff" opacity="0.08" filter="url(#uiNovaBloom)" />
				<path d="M 170 140 L 70 256 L 170 372" fill="none" stroke="#00f0ff" stroke-width="36" stroke-linecap="round" stroke-linejoin="round" />
				<path d="M 256 120 Q 256 256 160 256 Q 256 256 256 392 Q 256 256 352 256 Q 256 256 256 120 Z" fill="url(#uiNovaGrad)" filter="url(#uiNovaBloom)" />
				<circle cx="256" cy="256" r="22" fill="#ffffff" opacity="0.9" />
				<path d="M 342 140 L 442 256 L 342 372" fill="none" stroke="#00f0ff" stroke-width="36" stroke-linecap="round" stroke-linejoin="round" />
			</svg>
		</div>
	);
}

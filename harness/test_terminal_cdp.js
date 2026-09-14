const http = require('http');

async function main() {
    const list = await new Promise((resolve, reject) => {
        http.get('http://localhost:9222/json', (res) => {
            let raw = '';
            res.on('data', chunk => raw += chunk);
            res.on('end', () => resolve(JSON.parse(raw)));
        }).on('error', reject);
    });

    const target = list.find(t => t.type === 'page');
    console.log('Target:', target.title, target.url);
    const ws = new WebSocket(target.webSocketDebuggerUrl);

    let id = 1;
    const callbacks = new Map();

    function send(method, params = {}) {
        return new Promise((resolve, reject) => {
            const reqId = id++;
            callbacks.set(reqId, { resolve, reject });
            ws.send(JSON.stringify({ id: reqId, method, params }));
        });
    }

    ws.on('open', async () => {
        console.log('Connected to CDP');
        await send('Runtime.enable');
        await send('Console.enable');

        const checkTerminal = await send('Runtime.evaluate', {
            expression: `(() => {
                return {
                    hasWindowTerminal: typeof window.Terminal !== 'undefined',
                    hasTerminalIsInstalled: typeof window.Terminal?.isInstalled === 'function',
                    terminalKeys: window.Terminal ? Object.keys(window.Terminal) : []
                };
            })()`,
            returnByValue: true
        });
        console.log('Terminal check:', checkTerminal.result.value);

        console.log('Executing toggle-agent...');
        const toggleRes = await send('Runtime.evaluate', {
            expression: `(() => {
                acode.exec("toggle-agent");
                return "Toggled agent";
            })()`,
            returnByValue: true
        });
        console.log('Toggle result:', toggleRes.result.value);
    });

    ws.on('message', (data) => {
        const msg = JSON.parse(data);
        if (msg.id && callbacks.has(msg.id)) {
            const { resolve } = callbacks.get(msg.id);
            callbacks.delete(msg.id);
            resolve(msg);
        } else if (msg.method === 'Runtime.consoleAPICalled') {
            const args = msg.params.args.map(a => a.value || JSON.stringify(a)).join(' ');
            console.log('[Console]', msg.params.type, args);
        } else if (msg.method === 'Runtime.exceptionThrown') {
            console.error('[Exception]', msg.params.exceptionDetails);
        }
    });

    setTimeout(() => {
        console.log('Done listening.');
        process.exit(0);
    }, 4000);
}

main().catch(console.error);

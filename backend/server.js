const express = require('express');
const cors = require('cors');
const { MongoClient } = require('mongodb');

const app = express();
const port = 3000;

// 1. Enable CORS so React can talk to this server!
app.use(cors());
app.use(express.json());

const uri = "mongodb://localhost:27017";
const client = new MongoClient(uri);

// 2. GET Route: React fetches the nodes to draw the graph
app.get('/api/nodes', async (req, res) => {
    try {
        await client.connect();
        const db = client.db("netty_platform");
        const nodes = await db.collection("nodes").find({}).toArray();
        res.json(nodes);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// 3. POST Route: React saves a new node or updated graph to Mongo
app.post('/api/nodes', async (req, res) => {
    try {
        await client.connect();
        const db = client.db("netty_platform");
        
        const newNode = {
            name: req.body.name,
            code: req.body.code || "print('Hello World')",
            status: "idle"
        };
        
        const result = await db.collection("nodes").insertOne(newNode);
        res.json({ success: true, result });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.listen(port, () => {
    console.log(`🗄️ Express DB API running on http://localhost:${port}`);
});